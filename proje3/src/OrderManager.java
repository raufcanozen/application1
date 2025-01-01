import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

public class OrderManager{
    private static OrderManager instance;
    private final DatabaseManager dbManager;
    private final PriorityBlockingQueue<Order> orderQueue;
    private final Map<Integer, Customer> activeCustomers;
    private final ReentrantLock customerLock;
    private final ExecutorService orderProcessor;
    private final StockManager stockManager;
    private final LogManager logManager;
    private final List<Order> pendingOrders=new ArrayList<>();
    private final Semaphore orderProcessingSemaphore;
    private final ReentrantLock approvalLock;
    private final Map<Integer, ReentrantLock> customerLocks;
    private final ScheduledExecutorService priorityUpdater;
    private OrderManager(){
        this.dbManager=DatabaseManager.getInstance();
        this.orderQueue=new PriorityBlockingQueue<>(11,
                Comparator.comparingDouble(o->getCustomerById(o.getCustomerId()).getPriorityScore()));
        this.activeCustomers=new ConcurrentHashMap<>();
        this.customerLock=new ReentrantLock();
        this.orderProcessor=Executors.newFixedThreadPool(5);
        this.stockManager=StockManager.getInstance();
        this.logManager=LogManager.getInstance();
        this.orderProcessingSemaphore=new Semaphore(5);
        this.approvalLock=new ReentrantLock();
        this.customerLocks=new ConcurrentHashMap<>();
        this.priorityUpdater=Executors.newSingleThreadScheduledExecutor();
        startPriorityUpdates();
    }

    private void startPriorityUpdates() {
        priorityUpdater.scheduleAtFixedRate(()->{
            updatePriorities();
        },0,1,TimeUnit.SECONDS);
    }

    private void updatePriorities(){ //Burda lock etmemiz gerekiyor yoksa sistemimiz deadlock'a girer.
        customerLock.lock();
        try {
            for(int i=0;i<activeCustomers.values().size();i++){
                Customer customer=(Customer) activeCustomers.values().toArray()[i];
                customer.updatePriorityScore();}

            List<Order> currentOrders=new ArrayList<>();
            orderQueue.drainTo(currentOrders);
            currentOrders.sort((o1, o2)->{
                Customer c1=getCustomerById(o1.getCustomerId());
                Customer c2=getCustomerById(o2.getCustomerId());
                return Double.compare(c2.getPriorityScore(),c1.getPriorityScore());
            });
            orderQueue.addAll(currentOrders);}
        finally{
            customerLock.unlock();
        }
    }

    public static synchronized OrderManager getInstance() {
        if (instance == null) {
            instance = new OrderManager();
        }
        return instance;
    }

    public void startOrderProcessing(){
        orderProcessor.submit(this::processOrders);
    }

    private void processOrders() {
        while(!Thread.currentThread().isInterrupted()){
            try{
                Order order=orderQueue.take();
                if (order.getStatus() == Order.OrderStatus.PROCESSING){//Sadece siparişler onaylandıyssa ilerliyoruz
                    Customer customer = getCustomerById(order.getCustomerId());
                    if(customer!=null){
                        processOrder(order, customer);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void processOrder(Order order, Customer customer) {
        try {
            customer.setProcessing(true);
            order.setStatus(Order.OrderStatus.PROCESSING);

            // Validate stock availability
            boolean stockAvailable=validateStockAvailability(order); //Stoktan sistemde olduğunu onaylattık.
            if (!stockAvailable) {
                order.setStatus(Order.OrderStatus.FAILED);
                logManager.logError(customer.getCustomerId(), order.getOrderId(), "Yetersiz Stok");
                return;}

            if (customer.getBudget()<order.getTotalPrice()){ //Müşteri bütçesini onayladık.
                order.setStatus(Order.OrderStatus.FAILED);
                logManager.logError(customer.getCustomerId(), order.getOrderId(), "Müşteri bakiyesi yetersiz");
                return;}

            boolean success=true; //Talebi işleme aldık
            for(Map.Entry<Integer,Integer> entry:order.getProductQuantities().entrySet()) {
                success &= stockManager.decreaseStock(entry.getKey(), entry.getValue());
            }

            if (success){ //Müşterinin harcama miktarı ve bütçesini güncelledik
                customer.setBudget(customer.getBudget() - order.getTotalPrice());
                customer.addToTotalSpent(order.getTotalPrice());
                order.setStatus(Order.OrderStatus.COMPLETED);

                dbManager.updateCustomer(customer); //DBmizi güncelledik
                dbManager.createOrder(order);

                logManager.logInfo(customer.getCustomerId(), order.getOrderId(),
                        "Talep basariyla tamamlandi");}
            else{
                order.setStatus(Order.OrderStatus.FAILED);
                logManager.logError(customer.getCustomerId(), order.getOrderId(),
                        "Talep Tamamlanamadı");}
        } finally {
            customer.setProcessing(false);
            customer.setWaitingStartTime(0);
        }
    }

    public void addCustomer(Customer customer) {
        activeCustomers.put(customer.getCustomerId(), customer);
    }

    private boolean validateStockAvailability(Order order) {
        for (Map.Entry<Integer,Integer> entry :order.getProductQuantities().entrySet()){
            if (!stockManager.checkStock(entry.getKey(), entry.getValue())) {
                return false;}
        }
        return true;
    }


    public void submitOrder(Order order,Customer customer){
        customerLock.lock();
        try{
            customer.startWaiting();//Bekleme zamanını kontrol ediyoruz ki ileride öncelik puanını doğru hesaplayalım
            activeCustomers.put(customer.getCustomerId(),customer);
            pendingOrders.add(order);
            logManager.logInfo(customer.getCustomerId(),order.getOrderId(),
                    "Siprais eklendi ve onay bekliyor");}
        finally{
            customerLock.unlock();
        }
    }

    public Customer getCustomerById(int customerId) {
        return activeCustomers.get(customerId);
    }

    public void approveOrder(int... orderIds) {
        approvalLock.lock();
        try{
            Map<Integer, OrderApprovalTask> approvalTasks=new HashMap<>();
            Set<Integer> customerIds=new HashSet<>();

            for(int i = 0; i<orderIds.length;i++){ //Siparişlere eriştik ve onayladık
                int orderId=orderIds[i];
                OrderApprovalTask task=createApprovalTask(orderId);
                if(task!=null){
                    approvalTasks.put(orderId, task);
                    customerIds.add(task.getCustomerId());}
            }


            //Müşteri IDlerine göre sıraladık ki deadlock olmasın.
            List<Integer> sortedCustomerIds=new ArrayList<>(customerIds);
            Collections.sort(sortedCustomerIds);

            //Deadlockların önüne geçmek için lockumuzu aldık
            Map<Integer, ReentrantLock> lockedCustomers = new HashMap<>();
            try {
                for(int i=0;i<sortedCustomerIds.size();i++){
                    Integer customerId = sortedCustomerIds.get(i);
                    ReentrantLock customerLock=customerLocks.computeIfAbsent(
                            customerId,
                            k ->new ReentrantLock());
                    customerLock.lock();
                    lockedCustomers.put(customerId,customerLock);
                }


                for (OrderApprovalTask task : approvalTasks.values()){ //Sipraişleri onaylıyoruz
                    processApprovalTask(task);

                    Customer customer=getCustomerById(task.getCustomerId());//Bekleme süresini durdurdum
                    if (customer!=null) {
                        customer.stopWaiting();}
                }
            }
            finally{
                //Lockları bırakıyoruz burda.
                for (ReentrantLock lock : lockedCustomers.values()) {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            }
        } finally {
            approvalLock.unlock();
        }
    }

    private OrderApprovalTask createApprovalTask(int orderId) {
        try (Connection conn=dbManager.getConnection();
             PreparedStatement stmt=conn.prepareStatement(
                     "SELECT o.customer_id, o.total_price, o.product_id, o.quantity " +
                             "FROM orders o WHERE o.order_id = ? AND o.order_status = 'PENDING'")) {

            stmt.setInt(1, orderId);
            ResultSet rs=stmt.executeQuery();

            if(rs.next()){
                return new OrderApprovalTask(
                        orderId,
                        rs.getInt("customer_id"),
                        rs.getInt("product_id"),
                        rs.getInt("quantity"),
                        rs.getDouble("total_price")
                );
            }
        } catch (SQLException e) {
            logManager.logError(-1, orderId, "Siparis onayi yapilamadi: " + e.getMessage());
        }
        return null;
    }

    private void processApprovalTask(OrderApprovalTask task) {
        try {
            orderProcessingSemaphore.acquire();
            try{
                Customer customer=getCustomerById(task.getCustomerId());
                if(customer==null){
                    logManager.logError(task.getCustomerId(), task.getOrderId(),
                            "Musteri, onaylanma sirasinda bulunamadi");
                    return;
                }

                //Müşterinin bütçe ve harcaması onaylandı
                customer.setBudget(customer.getBudget()-task.getTotalPrice());
                customer.addToTotalSpent(task.getTotalPrice());
                dbManager.updateCustomer(customer);

                //Stok miktarı güncellendi
                stockManager.decreaseStock(task.getProductId(), task.getQuantity());

                //Siparis durumu güncellendi
                try (Connection conn = dbManager.getConnection();
                     PreparedStatement updateStmt = conn.prepareStatement(
                             "UPDATE orders SET order_status = 'COMPLETED' WHERE order_id = ?")) {
                    updateStmt.setInt(1, task.getOrderId());
                    updateStmt.executeUpdate();
                }

                logManager.logInfo(
                        task.getCustomerId(),
                        task.getOrderId(),
                        String.format("Order approved and processed: %d units of Product %d",
                                task.getQuantity(), task.getProductId())
                );
            } finally {
                orderProcessingSemaphore.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logManager.logError(task.getCustomerId(), task.getOrderId(),
                    "Order processing interrupted: " + e.getMessage());
        } catch (SQLException e) {
            logManager.logError(task.getCustomerId(), task.getOrderId(),
                    "Database error during approval: " + e.getMessage());
        }
    }

    private static class OrderApprovalTask {
        private final int orderId;
        private final int customerId;
        private final int productId;
        private final int quantity;
        private final double totalPrice;

        public OrderApprovalTask(int orderId, int customerId, int productId, int quantity, double totalPrice){
            this.orderId=orderId;
            this.customerId=customerId;
            this.productId=productId;
            this.quantity=quantity;
            this.totalPrice=totalPrice;
        }

        public int getOrderId() { return orderId; }
        public int getCustomerId() { return customerId; }
        public int getProductId() { return productId; }
        public int getQuantity() { return quantity; }
        public double getTotalPrice() { return totalPrice; }
    }

    public synchronized boolean requestItem(int customerId, int productId, int quantity) {
        if (quantity < 1 || quantity > 5) {
            LogManager.getInstance().logError(customerId, null,
                    "Invalid request: Quantity must be between 1 and 5.");
            return false;
        }

        Customer customer = getCustomerById(customerId);
        Product product = stockManager.getProduct(productId);

        if (customer == null || product == null) {
            LogManager.getInstance().logError(customerId, null,
                    "Invalid request: Customer or product not found.");
            return false;
        }

        double totalCost = quantity * product.getPrice();
        if (totalCost > customer.getBudget()) {
            LogManager.getInstance().logError(customerId, null,
                    "Request denied: Insufficient budget.");
            return false;
        }

        // Create new order
        Order order = new Order(0, customerId);
        order.addProduct(productId, quantity);
        order.setTotalPrice(totalCost);
        order.setStatus(Order.OrderStatus.PENDING);

        // Save to database first to get order ID
        Order savedOrder = dbManager.createOrder(order);

        if (savedOrder != null) {
            // Now use submitOrder instead of directly adding to queue
            submitOrder(savedOrder, customer);
            return true;
        }

        return false;
    }

    public Collection<Customer> getActiveCustomers(){
        return activeCustomers.values();
    }

    public void shutdown() {
        orderProcessor.shutdown();
        try{
            if (!orderProcessor.awaitTermination(60,TimeUnit.SECONDS)){
                orderProcessor.shutdownNow();}
        } catch (InterruptedException e){
            orderProcessor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}