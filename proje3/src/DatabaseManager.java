import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class DatabaseManager {
    private static final String DB_URL = "jdbc:mysql://localhost:3306/stock_manager";
    private static final String USER = "root";
    private static final String PASS = "Yeniadam90!";
    private static DatabaseManager instance;
    private final ReentrantLock lock = new ReentrantLock();

    private DatabaseManager() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            System.out.println("Driver yuklendi");
            initializeDatabase();
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("MySQL JDBC Driver not found", e);
        }
    }

    //DatabaseManager tipinden donus yapıyor
    public static synchronized DatabaseManager getInstance() {
        if(instance==null) {
            instance= new DatabaseManager();
        }
        return instance;
    }

    private void initializeDatabase(){
        try (Connection conn= getConnection()) {
            System.out.println("Database baglantisi basarili");
            createTables(conn);
            initializeDefaultData(conn);
        } catch(SQLException e) {
            e.printStackTrace();
            System.err.println("Failed to initialize database. Exiting...");
            System.exit(1);
        }
    }

    private void createTables(Connection conn) throws SQLException {
        String[] createTableQueries= {
                // Musteri Tablosu
                """
            CREATE TABLE IF NOT EXISTS customers (
                customer_id INT PRIMARY KEY AUTO_INCREMENT,
                customer_name VARCHAR(100) NOT NULL,
                budget DOUBLE NOT NULL,
                customer_type VARCHAR(20) NOT NULL,
                total_spent DOUBLE DEFAULT 0
            )
            """,
                // Urun Tablosu
                """
            CREATE TABLE IF NOT EXISTS products (
                product_id INT PRIMARY KEY AUTO_INCREMENT,
                product_name VARCHAR(100) NOT NULL,
                stock INT NOT NULL,
                price DOUBLE NOT NULL,
                is_deleted BOOLEAN DEFAULT FALSE
            )
            """,
                // Talepler tablosu
                """
            CREATE TABLE IF NOT EXISTS orders (
            order_id INT PRIMARY KEY AUTO_INCREMENT,
            customer_id INT,
            product_id INT,
            quantity INT NOT NULL,
            total_price DOUBLE NOT NULL,
            order_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            order_status VARCHAR(20) NOT NULL,
            FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
            FOREIGN KEY (product_id) REFERENCES products(product_id)
        )
        """,
                // Talep detaylari
                """
            CREATE TABLE IF NOT EXISTS order_details (
                order_id INT,
                product_id INT,
                quantity INT NOT NULL,
                FOREIGN KEY (order_id) REFERENCES orders(order_id),
                FOREIGN KEY (product_id) REFERENCES products(product_id),
                PRIMARY KEY (order_id, product_id)
            )
            """,
                // Log Tablolari
                """
            CREATE TABLE IF NOT EXISTS logs (
                 log_id INT PRIMARY KEY AUTO_INCREMENT,
                 customer_id INT,
                 order_id INT,
                 log_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                 log_type VARCHAR(20) NOT NULL,
                 log_details TEXT,
                 FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
                 FOREIGN KEY (order_id) REFERENCES orders(order_id)
             )

            """
        };

        try (Statement stmt =conn.createStatement()) {
            for (String query : createTableQueries) {
                stmt.execute(query);
            }
        }
    }

    public synchronized boolean customersExist() {
        String sql = "SELECT COUNT(*) FROM customers";
        try (Connection conn= getConnection();
             Statement stmt= conn.createStatement();
             ResultSet rs= stmt.executeQuery(sql)) {
            if (rs.next()){
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public synchronized List<Customer> getAllCustomers() {
        List<Customer> customers= new ArrayList<>();
        String sql = "SELECT * FROM customers";
        try (Connection conn= getConnection();
             Statement stmt= conn.createStatement();
             ResultSet rs= stmt.executeQuery(sql)) {

            while (rs.next()) {
                Customer customer = new Customer(
                        rs.getInt("customer_id"),
                        rs.getString("customer_name"),
                        rs.getDouble("budget"),
                        Customer.CustomerType.valueOf(rs.getString("customer_type"))
                );
                customer.setTotalSpent(rs.getDouble("total_spent"));
                //Default değerlerle runtime degerleri atadik.
                if (customer.getCustomerType() ==Customer.CustomerType.PREMIUM){
                    customer.setPriorityScore(15);}
                else{
                    customer.setPriorityScore(10);}

                customer.setWaitingStartTime(0);
                customer.setProcessing(false);
                customers.add(customer);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return customers;
    }

    private void initializeDefaultData(Connection conn) throws SQLException {
        // Urun tablosunun boş olup olmadigini kontrol ettim.
        String checkProducts ="SELECT COUNT(*) FROM products";
        try (Statement stmt =conn.createStatement();
             ResultSet rs =stmt.executeQuery(checkProducts)) {
            rs.next();
            if (rs.getInt(1)==0){
                String[] productInserts={
                        "INSERT INTO products (product_name, stock, price) VALUES ('Product1', 500, 100)",
                        "INSERT INTO products (product_name, stock, price) VALUES ('Product2', 10, 50)",
                        "INSERT INTO products (product_name, stock, price) VALUES ('Product3', 200, 45)",
                        "INSERT INTO products (product_name, stock, price) VALUES ('Product4', 75, 75)",
                        "INSERT INTO products (product_name, stock, price) VALUES ('Product5', 0, 500)"
                };

                for (String insert :productInserts){
                    stmt.execute(insert);
                }
                System.out.println("Default ürünler yüklendi");
            }
        }
    }

    public Connection getConnection() throws SQLException{
        return DriverManager.getConnection(DB_URL,USER,PASS);
    }

    // Musterilerle ilgili islemler
    public synchronized Customer createCustomer(String name, double budget, Customer.CustomerType type) {
        String sql ="INSERT INTO customers (customer_name, budget, customer_type) VALUES (?, ?, ?)";
        try (Connection conn =getConnection();
             PreparedStatement pstmt =conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)){
            pstmt.setString(1,name);
            pstmt.setDouble(2,budget);
            pstmt.setString(3,type.toString());

            pstmt.executeUpdate();

            try (ResultSet rs= pstmt.getGeneratedKeys()){
                if (rs.next()){
                    return new Customer(rs.getInt(1), name, budget, type);}
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public synchronized void updateCustomer(Customer customer) {
        String sql ="UPDATE customers SET budget = ?, customer_type = ?, total_spent = ? WHERE customer_id = ?";
        try (Connection conn= getConnection();
             PreparedStatement pstmt= conn.prepareStatement(sql)){
            pstmt.setDouble(1,customer.getBudget());
            pstmt.setString(2, customer.getCustomerType().toString());
            pstmt.setDouble(3, customer.getTotalSpent());
            pstmt.setInt(4,customer.getCustomerId());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Urun islemleri
    public synchronized void updateProductStock(int productId, int newStock) {
        String sql ="UPDATE products SET stock = ? WHERE product_id = ?";
        try (Connection conn =getConnection();
             PreparedStatement pstmt= conn.prepareStatement(sql)) {

            pstmt.setInt(1, newStock);
            pstmt.setInt(2,productId);
            pstmt.executeUpdate();
        } catch (SQLException e){
            e.printStackTrace();
        }
    }

    public List<Product> getAllProducts() {
        List<Product> products =new ArrayList<>();
        String sql ="SELECT * FROM products WHERE is_deleted = FALSE";
        try (Connection conn =getConnection();
             Statement stmt =conn.createStatement();
             ResultSet rs =stmt.executeQuery(sql)){
            while (rs.next()){
                products.add(new Product(
                        rs.getInt("product_id"),
                        rs.getString("product_name"),
                        rs.getInt("stock"),
                        rs.getDouble("price")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return products;
    }

    // Log islemleri
    public void createLog(int customerId, Integer orderId, String logType, String details) {
        String sql ="INSERT INTO logs (customer_id, order_id, log_type, log_details) VALUES (?, ?, ?, ?)";
        try (Connection conn =getConnection();
             PreparedStatement pstmt= conn.prepareStatement(sql)) {
            if (customerId ==-1){
                pstmt.setNull(1, java.sql.Types.INTEGER);}
            else{
                pstmt.setInt(1, customerId);}
            pstmt.setObject(2,orderId);
            pstmt.setString(3,logType);
            pstmt.setString(4,details);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public synchronized Order createOrder(Order order) {
        String sql ="INSERT INTO orders (customer_id, product_id, quantity, total_price, order_status) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn =getConnection();
             PreparedStatement pstmt =conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setInt(1,order.getCustomerId());
            Map.Entry<Integer, Integer> productEntry =order.getProductQuantities().entrySet().iterator().next();
            pstmt.setInt(2,productEntry.getKey());
            pstmt.setInt(3,productEntry.getValue());
            pstmt.setDouble(4,order.getTotalPrice());
            pstmt.setString(5,order.getStatus().toString());
            pstmt.executeUpdate();
            try (ResultSet rs =pstmt.getGeneratedKeys()) {
                if (rs.next()){
                    order.setOrderId(rs.getInt(1));
                    return order;}
            }
        } catch (SQLException e){
            e.printStackTrace();
        }
        return null;
    }
}