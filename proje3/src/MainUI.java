import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

public class MainUI extends JFrame {
    private final OrderManager orderManager;
    private final StockManager stockManager;
    private final LogManager logManager;


    private JTable customerTable;
    private JTable productTable;
    private JTextArea logArea;
    private DefaultTableModel customerTableModel;
    private DefaultTableModel productTableModel;
    private Timer updateTimer;
    private long lastLogId = 0;

    public MainUI(){
        this.orderManager=OrderManager.getInstance();
        this.stockManager= StockManager.getInstance();
        this.logManager= LogManager.getInstance();

        setTitle(" Eş Zamanlı Sipariş ve Stok Yönetimi Uygulaması");
        setSize(1200,800);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        initializeComponents();
        startUpdateTimer();
        updateLogArea();
    }

    private void initializeComponents(){
        setLayout(new BorderLayout());

        //Main'i böldük
        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);

        //Üst kısmı musteri ve urun tablolarına böldük.
        JSplitPane upperPane =new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        upperPane.setLeftComponent(createCustomerPanel());
        upperPane.setRightComponent(createProductPanel());

        // Alt paneli loglara ayırdık
        JPanel lowerPane = createLogPanel();
        mainSplitPane.setTopComponent(upperPane);
        mainSplitPane.setBottomComponent(lowerPane);
        mainSplitPane.setDividerLocation(400);
        add(mainSplitPane, BorderLayout.CENTER);
        add(createToolbar(), BorderLayout.NORTH);
    }

    private JPanel createCustomerPanel(){
        JPanel panel =new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Musteriler"));

        String[] columnNames = {"ID", "Ad", "Tur","Butce", "Priority Score","Durum"};
        customerTableModel =new DefaultTableModel(columnNames, 0){
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        customerTable =new JTable(customerTableModel);
        JScrollPane scrollPane=new JScrollPane(customerTable);
        panel.add(scrollPane,BorderLayout.CENTER);
        return panel;
    }

    private JPanel createProductPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Products"));

        String[] columnNames = {"ID", "Name", "Stock", "Price"};
        productTableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        productTable =new JTable(productTableModel);
        JScrollPane scrollPane =new JScrollPane(productTable);
        panel.add(scrollPane,BorderLayout.CENTER);
        return panel;
    }

    private JPanel createLogPanel() {
        JPanel panel =new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("System Logs"));

        logArea =new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12)); // Using monospaced font for better alignment
        JScrollPane scrollPane =new JScrollPane(logArea);
        scrollPane.setPreferredSize(new Dimension(getWidth(), 200));
        panel.add(scrollPane, BorderLayout.CENTER);
        JButton clearButton = new JButton("Clear Logs");
        clearButton.addActionListener(e -> {
            logArea.setText("");
            lastLogId = 0;
        });
        panel.add(clearButton, BorderLayout.SOUTH);
        return panel;
    }

    private JToolBar createToolbar(){
        JToolBar toolbar =new JToolBar();
        toolbar.setFloatable(false);
        JButton addProductBtn =new JButton("Urun Ekle");
        JButton deleteProductBtn =new JButton("Urun Sil");
        JButton updateProductBtn =new JButton("Urunu Update Et");
        addProductBtn.addActionListener(e ->showAddProductDialog());
        deleteProductBtn.addActionListener(e ->showDeleteProductDialog());
        updateProductBtn.addActionListener(e ->showUpdateProductDialog());
        JButton requestProductBtn =new JButton("Siparis Verme");
        JButton approveRequestBtn =new JButton("Siparis Onaylama");
        requestProductBtn.addActionListener(e ->showRequestProductDialog());
        approveRequestBtn.addActionListener(e ->showApproveRequestsDialog());
        toolbar.add(requestProductBtn);
        toolbar.add(approveRequestBtn);
        toolbar.add(addProductBtn);
        toolbar.add(deleteProductBtn);
        toolbar.add(updateProductBtn);
        return toolbar;
    }

    private void showRequestProductDialog() {
        JDialog dialog = new JDialog(this, "Request Product", true);
        dialog.setSize(500, 400);
        dialog.setLayout(new BorderLayout(10, 10));

        //Main paneli yarattık
        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);

        //Müsteri kısmı
        JComboBox<String> customerCombo = new JComboBox<>();
        for (Customer customer:orderManager.getActiveCustomers()) {
            customerCombo.addItem(String.format("%s (ID: %d) - Butce: %.2f",
                    customer.getCustomerName(),
                    customer.getCustomerId(),
                    customer.getBudget()));
        }

        // Urun Kısmı
        JComboBox<String> productCombo =new JComboBox<>();
        for (Product product :stockManager.getAllProducts()){
            if (!product.isDeleted() && product.getStock() >0){
                productCombo.addItem(String.format("%s (ID: %d) - Stok: %d - Fiyat: %.2f",
                        product.getProductName(),
                        product.getProductId(),
                        product.getStock(),
                        product.getPrice()));}
        }

        SpinnerModel spinnerModel = new SpinnerNumberModel(1,1,5,1);
        JSpinner quantitySpinner =new JSpinner(spinnerModel);

        // Panele ekledik
        gbc.gridx=0; gbc.gridy=0;
        mainPanel.add(new JLabel("Musteri:"), gbc);
        gbc.gridx=1;
        mainPanel.add(customerCombo,gbc);
        gbc.gridx =0; gbc.gridy =1;
        mainPanel.add(new JLabel("Urun:"),gbc);
        gbc.gridx=1;
        mainPanel.add(productCombo, gbc);
        gbc.gridx=0; gbc.gridy=2;
        mainPanel.add(new JLabel("Miktar:"), gbc);
        gbc.gridx=1;
        mainPanel.add(quantitySpinner, gbc);

        // Butonlar
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton requestButton = new JButton("İlerle");
        JButton cancelButton = new JButton("İptal");
        requestButton.addActionListener(e ->{
            String customerStr =(String) customerCombo.getSelectedItem();
            String productStr =(String) productCombo.getSelectedItem();
            if (customerStr !=null && productStr !=null){
                int customerId =Integer.parseInt(customerStr.split("ID: ")[1].split("\\)")[0]);
                int productId =Integer.parseInt(productStr.split("ID: ")[1].split("\\)")[0]);
                int quantity =(Integer) quantitySpinner.getValue();
                boolean success =orderManager.requestItem(customerId, productId, quantity);
                if(success){
                    JOptionPane.showMessageDialog(dialog,
                            "Siparis Basarılı bir sekilde eklendi",
                            "Basarili",
                            JOptionPane.INFORMATION_MESSAGE);
                    dialog.dispose();
                }
            }
        });
        cancelButton.addActionListener(e ->dialog.dispose());
        buttonPanel.add(requestButton);
        buttonPanel.add(cancelButton);
        dialog.add(mainPanel,BorderLayout.CENTER);
        dialog.add(buttonPanel,BorderLayout.SOUTH);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void showApproveRequestsDialog() {
        JDialog dialog =new JDialog(this, "Siparisi Onayla", true);
        dialog.setSize(600,400);
        dialog.setLayout(new BorderLayout(10, 10));

        String[] columnNames = {"Siparis ID", "Musteri", "Urun", "Adet", "Toplam Fiyat", "Statu"};
        DefaultTableModel model = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        JTable ordersTable =new JTable(model);
        ordersTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        JScrollPane scrollPane =new JScrollPane(ordersTable);

        // Tabloyu siparislerle doldurduk
        DatabaseManager dbManager =DatabaseManager.getInstance();
        try (Connection conn =dbManager.getConnection();
             PreparedStatement stmt =conn.prepareStatement(
                     "SELECT o.order_id, o.customer_id, o.total_price, o.order_status, o.product_id, o.quantity FROM orders o WHERE o.order_status = ?")){
            stmt.setString(1,"PENDING");
            ResultSet rs =stmt.executeQuery();
            while (rs.next()){
                int orderId =rs.getInt("order_id");
                int customerId =rs.getInt("customer_id");
                int productId =rs.getInt("product_id");
                int quantity =rs.getInt("quantity");
                double totalPrice =rs.getDouble("total_price");
                String status =rs.getString("order_status");

                // Get customer and product information
                Customer customer = orderManager.getCustomerById(customerId);
                Product product = stockManager.getProduct(productId);

                if (customer !=null && product!= null) {
                    Vector<Object> row =new Vector<>();
                    row.add(orderId);
                    row.add(customer.getCustomerName());
                    row.add(product.getProductName());
                    row.add(quantity);
                    row.add(String.format("%.2f", totalPrice));
                    row.add(status);
                    model.addRow(row);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(dialog,
                    "Pending siparisler yuklenemedi: " + e.getMessage(),
                    "Database Hatasi",
                    JOptionPane.ERROR_MESSAGE);
        }

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton approveButton = new JButton("Secilenleri Onayla");
        JButton cancelButton = new JButton("İptal");
        approveButton.addActionListener(e ->{
            int[] selectedRows = ordersTable.getSelectedRows();
            if (selectedRows.length >0){
                int[] orderIds =new int[selectedRows.length];
                for (int i=0; i<selectedRows.length; i++){
                    orderIds[i] =(Integer) model.getValueAt(selectedRows[i], 0);
                }
                orderManager.approveOrder(orderIds);
                for (int i=selectedRows.length- 1; i>=0; i--){
                    model.removeRow(selectedRows[i]);
                }
                updateCustomerTable();
                updateProductTable();
                JOptionPane.showMessageDialog(dialog,
                        selectedRows.length + "Siparis(ler) onaylandı",
                        "Basarili",
                        JOptionPane.INFORMATION_MESSAGE);}
            else{
                JOptionPane.showMessageDialog(dialog,
                        "Onaylamak icin en az bir tane secin",
                        "Secilmedi",
                        JOptionPane.WARNING_MESSAGE);
            }
        });
        cancelButton.addActionListener(e ->dialog.dispose());
        buttonPanel.add(approveButton);
        buttonPanel.add(cancelButton);
        dialog.add(scrollPane,BorderLayout.CENTER);
        dialog.add(buttonPanel,BorderLayout.SOUTH);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void showAddProductDialog() {
        JDialog dialog = new JDialog(this, "Urun Ekle",true);
        dialog.setSize(400,300);
        dialog.setLayout(new BorderLayout(10, 10));

        JPanel mainPanel=new JPanel(new GridBagLayout());//Burada ana paneli yarattım
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));
        GridBagConstraints gbc=new GridBagConstraints();
        gbc.gridwidth=1;
        gbc.fill=GridBagConstraints.HORIZONTAL;
        gbc.insets=new Insets(5,5,5,5);

        JTextField nameField = new JTextField(20);//Burada input kısımlarını ekledim
        JTextField stockField = new JTextField(20);
        JTextField priceField = new JTextField(20);

        gbc.gridx=0;
        gbc.gridy=0;
        mainPanel.add(new JLabel("Urun adi:"),gbc);
        gbc.gridx=1;
        mainPanel.add(nameField,gbc);

        gbc.gridx=0;
        gbc.gridy=1;
        mainPanel.add(new JLabel("Stok:"),gbc);
        gbc.gridx=1;
        mainPanel.add(stockField, gbc);

        gbc.gridx=0;
        gbc.gridy=2;
        mainPanel.add(new JLabel("Fiyat:"), gbc);
        gbc.gridx=1;
        mainPanel.add(priceField,gbc);

        JPanel buttonPanel=new JPanel(new FlowLayout(FlowLayout.RIGHT));//Buton paneli
        JButton saveButton=new JButton("Kaydet");
        JButton cancelButton=new JButton("İptal Et");
        saveButton.addActionListener(e->{
            try{
                String name = nameField.getText().trim();
                if (name.isEmpty()){
                    throw new IllegalArgumentException("Urun adi bos olamaz");
                }

                int stock=Integer.parseInt(stockField.getText().trim());
                if(stock<0){
                    throw new IllegalArgumentException("Stok miktari negatif olamaz");
                }

                double price=Double.parseDouble(priceField.getText().trim());
                if(price<=0){
                    throw new IllegalArgumentException("Fiyat sifirdan buyuk olamlidir");
                }

                Product product=new Product(0, name, stock, price);
                stockManager.addProduct(product);
                dialog.dispose();}
            catch(NumberFormatException ex){
                JOptionPane.showMessageDialog(dialog,
                        "Lutfen urun ve fiyat icin dogru degerler giriniz",
                        "Input Hatasi",
                        JOptionPane.ERROR_MESSAGE);}
            catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(dialog,
                        ex.getMessage(),
                        "Input Hatasi",
                        JOptionPane.ERROR_MESSAGE);}
        });
        cancelButton.addActionListener(e->dialog.dispose());
        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);

        dialog.add(mainPanel,BorderLayout.CENTER);
        dialog.add(buttonPanel,BorderLayout.SOUTH);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void showDeleteProductDialog() {
        JDialog dialog=new JDialog(this,"Urunu Sil",true);
        dialog.setSize(400,300);
        dialog.setLayout(new BorderLayout(10,10));

        DefaultListModel<String> listModel=new DefaultListModel<>();//Liste modeli ve liste yarattık.
        JList<String> productList=new JList<>(listModel);

        Map<Integer, Product> productMap = new HashMap<>();//Listeyi ürünlerle donattık
        for (Product product:stockManager.getAllProducts()){
            if(!product.isDeleted()){
                listModel.addElement(product.getProductName());
                productMap.put(listModel.size()-1,product);
            }
        }

        JScrollPane scrollPane=new JScrollPane(productList);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));

        //Button paneli
        JPanel buttonPanel=new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton deleteButton=new JButton("Sil");
        JButton cancelButton=new JButton("İptal Et");

        deleteButton.addActionListener(e->{
            int selectedIndex = productList.getSelectedIndex();
            if (selectedIndex!=-1){
                Product selectedProduct=productMap.get(selectedIndex);
                int confirm=JOptionPane.showConfirmDialog(dialog,
                        "Belirtilen urunu silmek istediginize emin misiniz?: " + selectedProduct.getProductName() + "?",
                        "Silmeyi Onayla",
                        JOptionPane.YES_NO_OPTION);
                if(confirm==JOptionPane.YES_OPTION){
                    stockManager.removeProduct(selectedProduct.getProductId());
                    dialog.dispose();
                }
            }
            else{
                JOptionPane.showMessageDialog(dialog,
                        "Lutfen silmek istediginiz urunu secin",
                        "Secim Yapilmadi",
                        JOptionPane.WARNING_MESSAGE);
            }
        });

        cancelButton.addActionListener(e->dialog.dispose());

        buttonPanel.add(deleteButton);
        buttonPanel.add(cancelButton);

        dialog.add(scrollPane,BorderLayout.CENTER);
        dialog.add(buttonPanel,BorderLayout.SOUTH);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void showUpdateProductDialog() {
        JDialog dialog=new JDialog(this,"Urunu Guncelle", true);
        dialog.setSize(600,400);
        dialog.setLayout(new BorderLayout(10,10));

        JSplitPane splitPane=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT); //Yeni GUI'yi böldük
        splitPane.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));

        JPanel listPanel=new JPanel(new BorderLayout());
        listPanel.setBorder(BorderFactory.createTitledBorder("Urunu Secin"));

        DefaultListModel<String> listModel=new DefaultListModel<>();
        JList<String> productList=new JList<>(listModel);
        productList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        productList.setBorder(BorderFactory.createEmptyBorder(5,5,5,5));

        Map<Integer, Product> productMap=new HashMap<>();
        for (Product product:stockManager.getAllProducts()) {
            if(!product.isDeleted()){
                listModel.addElement(String.format("%s (ID: %d)", product.getProductName(), product.getProductId()));
                productMap.put(listModel.size() - 1, product);
            }
        }

        JScrollPane listScrollPane=new JScrollPane(productList);
        listPanel.add(listScrollPane,BorderLayout.CENTER);

        JPanel formPanel=new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createTitledBorder("Urun Detaylari"));

        GridBagConstraints gbc=new GridBagConstraints();
        gbc.gridwidth=1;
        gbc.fill=GridBagConstraints.HORIZONTAL;
        gbc.insets=new Insets(5,10,5,10);
        gbc.anchor=GridBagConstraints.WEST;

        JTextField nameField = new JTextField(20);
        JTextField stockField = new JTextField(20);
        JTextField priceField = new JTextField(20);

        gbc.gridx=0;
        gbc.gridy=0;
        gbc.weightx=0.3;
        formPanel.add(new JLabel("Urun Adi:"),gbc);
        gbc.gridx=1;
        gbc.weightx=0.7;
        formPanel.add(nameField,gbc);

        gbc.gridx=0;
        gbc.gridy=1;
        gbc.weightx=0.3;
        formPanel.add(new JLabel("Stok:"),gbc);
        gbc.gridx=1;
        gbc.weightx=0.7;
        formPanel.add(stockField, gbc);

        gbc.gridx=0;
        gbc.gridy=2;
        gbc.weightx=0.3;
        formPanel.add(new JLabel("Fiyat:"),gbc);
        gbc.gridx=1;
        gbc.weightx=0.7;
        formPanel.add(priceField,gbc);

        stockField.setInputVerifier(new InputVerifier(){
            @Override
            public boolean verify(JComponent input){
                try{
                    int value=Integer.parseInt(((JTextField) input).getText().trim());
                    return value>=0;}
                catch(NumberFormatException e){
                    return false;
                }
            }
        });

        priceField.setInputVerifier(new InputVerifier(){
            @Override
            public boolean verify(JComponent input){
                try{
                    double value=Double.parseDouble(((JTextField) input).getText().trim());
                    return value>0;
                }
                catch(NumberFormatException e){
                    return false;
                }
            }
        });

        productList.addListSelectionListener(e->{
            if(!e.getValueIsAdjusting()){
                int selectedIndex=productList.getSelectedIndex();
                if(selectedIndex!=-1){
                    Product selected=productMap.get(selectedIndex);
                    nameField.setText(selected.getProductName());
                    stockField.setText(String.valueOf(selected.getStock()));
                    priceField.setText(String.format("%.2f", selected.getPrice()));
                }
            }
        });

        JPanel buttonPanel=new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        JButton updateButton=new JButton("Guncelle");
        JButton cancelButton=new JButton("Iptal");

        updateButton.addActionListener(e->{
            int selectedIndex=productList.getSelectedIndex();
            if(selectedIndex!=-1){
                try{
                    Product selectedProduct=productMap.get(selectedIndex);
                    String name=nameField.getText().trim();
                    if(name.isEmpty()){
                        throw new IllegalArgumentException("Urun adi bos olamaz");
                    }
                    int stock=Integer.parseInt(stockField.getText().trim());
                    double price=Double.parseDouble(priceField.getText().trim());

                    selectedProduct.setProductName(name);
                    selectedProduct.setStock(stock);
                    selectedProduct.setPrice(price);

                    stockManager.addProduct(selectedProduct);
                    dialog.dispose();
                }
                catch(NumberFormatException ex){
                    JOptionPane.showMessageDialog(dialog,
                            "Lutfen stok ve fiyat icin uygun sayilar girin",
                            "Input Hatasi",
                            JOptionPane.ERROR_MESSAGE);
                }
                catch (IllegalArgumentException ex){
                    JOptionPane.showMessageDialog(dialog,
                            ex.getMessage(),
                            "Input Hatasi",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
            else{
                JOptionPane.showMessageDialog(dialog,
                        "Guncellemek istediginiz urunu secin",
                        "Secim Yapilmadi",
                        JOptionPane.WARNING_MESSAGE);
            }
        });

        cancelButton.addActionListener(e->dialog.dispose());

        buttonPanel.add(updateButton);
        buttonPanel.add(cancelButton);

        splitPane.setLeftComponent(listPanel);
        splitPane.setRightComponent(formPanel);
        splitPane.setDividerLocation(250);

        dialog.add(splitPane, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void startUpdateTimer(){
        updateTimer=new Timer(1000,(ActionEvent e)->{
            updateCustomerTable();
            updateProductTable();
            updateLogArea();
        });
        updateTimer.start();
    }

    private void updateCustomerTable() {
        customerTableModel.setRowCount(0);
        for (Customer customer : orderManager.getActiveCustomers()) {
            Vector<Object> row = new Vector<>();
            row.add(customer.getCustomerId());
            row.add(customer.getCustomerName());
            row.add(customer.getCustomerType());
            row.add(customer.getBudget());
            row.add(String.format("%.2f", customer.getPriorityScore()));

            String status = "Beklemede";
            try (Connection conn = DatabaseManager.getInstance().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT order_status FROM orders WHERE customer_id = ? ORDER BY order_id DESC LIMIT 1")) {
                stmt.setInt(1, customer.getCustomerId());
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    String dbStatus = rs.getString("order_status");
                    switch (dbStatus) {
                        case "PENDING":
                            status = "Onay Bekliyor";
                            break;
                        case "PROCESSING":
                            status = "Devam Ediyor";
                            break;
                        case "COMPLETED":
                            status = "Tamamlandı";
                            break;
                        case "FAILED":
                            status = "Başarısız";
                            break;
                        default:
                            status = "Bilinmeyen";
                            break;
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }

            row.add(status);
            customerTableModel.addRow(row);
        }
    }

    private void updateProductTable() {
        productTableModel.setRowCount(0);
        List<Product> products=stockManager.getAllProducts();
        for (Product product : products){
            if (!product.isDeleted()){
                Vector<Object> row=new Vector<>();
                row.add(product.getProductId());
                row.add(product.getProductName());
                row.add(product.getStock());
                row.add(product.getPrice());
                productTableModel.addRow(row);
            }
        }
    }

    private void updateLogArea() {
        try (Connection conn=DatabaseManager.getInstance().getConnection();
             PreparedStatement stmt=conn.prepareStatement(
                     "SELECT log_id,customer_id,order_id,log_date,log_type,log_details "+
                             "FROM logs WHERE log_id > ? ORDER BY log_id ASC")){

            stmt.setLong(1, lastLogId);
            ResultSet rs = stmt.executeQuery();

            StringBuilder newLogs=new StringBuilder();
            while (rs.next()){
                lastLogId=rs.getLong("log_id");
                String timestamp=rs.getTimestamp("log_date").toString();
                String logType=rs.getString("log_type");
                String details=rs.getString("log_details");
                Integer customerId=rs.getInt("customer_id");
                Integer orderId;
                if (rs.getObject("order_id")!=null){
                    orderId=rs.getInt("order_id");}
                else{
                    orderId = null;}


                String logEntry;
                String customer;
                String orderInfo;
                if (customerId==0){
                    customer = "Sistem";}
                else{
                    customer = String.valueOf(customerId);}

                if(orderId!=null){
                    orderInfo=",Siparis: " + orderId;}
                else{
                    orderInfo = "";}
                logEntry = String.format("[%s] %s: %s (Musteri: %s%s)%n",
                        timestamp, logType, details, customer, orderInfo);
                newLogs.append(logEntry);
            }

            // Append new logs to the log area
            if(newLogs.length()>0){
                logArea.append(newLogs.toString());
                //Otomatikmen en aşağıya git
                logArea.setCaretPosition(logArea.getDocument().getLength());
            }
        }catch (SQLException e){
            e.printStackTrace();
        }
    }

    public void shutdown() {
        updateTimer.stop();
    }
}