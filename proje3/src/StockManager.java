import java.sql.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class StockManager {
    private static StockManager instance;
    private final DatabaseManager dbManager;
    private final Map<Integer, Product> products;
    private final ReentrantLock stockLock;

    private StockManager(){
        this.dbManager=DatabaseManager.getInstance();
        this.products=new ConcurrentHashMap<>();
        this.stockLock=new ReentrantLock();
        loadProducts();
    }

    public static synchronized StockManager getInstance(){
        if(instance==null){
            instance=new StockManager();
        }
        return instance;
    }

    private void loadProducts(){
        List<Product> productList=dbManager.getAllProducts();
        for(int i = 0; i < productList.size(); i++){
            Product product=productList.get(i);
            products.put(product.getProductId(),product);
        }
    }

    public synchronized boolean decreaseStock(int productId, int quantity){
        stockLock.lock(); //Deadlock'u önlemek için lock'a ihtiyacımız var
        try{
            Product product=products.get(productId);
            if (product!=null && product.getStock()>=quantity){
                int newStock=product.getStock()-quantity;
                product.setStock(newStock);
                dbManager.updateProductStock(productId,newStock);
                return true;
            }
            return false;}
        finally{
            stockLock.unlock();}
    }

    public boolean checkStock(int productId, int quantity){
        Product product=products.get(productId);
        return product!=null&&product.getStock()>=quantity;
    }

    public Product getProduct(int productId){
        return products.get(productId);
    }

    public List<Product> getAllProducts() {
        return List.copyOf(products.values());
    }

    public void addProduct(Product product) {
        stockLock.lock();
        try{
            String sql;
            if (product.getProductId()==0){
                //Yeni ürün
                sql = "INSERT INTO products (product_name, stock, price) VALUES (?, ?, ?)";
            } else {
                //Varolanı güncellemek için
                sql = "UPDATE products SET product_name = ?, stock = ?, price = ? WHERE product_id = ?";
            }

            try(Connection conn=dbManager.getConnection();
                 PreparedStatement pstmt=conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)){
                pstmt.setString(1,product.getProductName());
                pstmt.setInt(2,product.getStock());
                pstmt.setDouble(3,product.getPrice());
                if(product.getProductId()!=0){
                    pstmt.setInt(4,product.getProductId());
                }
                pstmt.executeUpdate();
                if (product.getProductId()==0){
                    //Yeni eklenen ürünün ID'sini aldık.
                    try (ResultSet rs = pstmt.getGeneratedKeys()){
                        if (rs.next()){
                            product.setProductId(rs.getInt(1));
                        }
                    }
                }
                // Lokal cache'i update ettik
                products.put(product.getProductId(), product);

                LogManager.getInstance().logInfo(
                        -1,
                        null,
                        String.format("%s product: %s (ID: %d) with stock: %d and price: %.2f",
                                product.getProductId() == 0 ? "Added new" : "Updated",
                                product.getProductName(),
                                product.getProductId(),
                                product.getStock(),
                                product.getPrice())
                );
            } catch (SQLException e) {
                e.printStackTrace();
                LogManager.getInstance().logError(
                        -1,
                        null,
                        "Failed to " + (product.getProductId() == 0 ? "add" : "update") + " product: " + e.getMessage()
                );
            }
        } finally{
            stockLock.unlock();
        }
    }

    public void removeProduct(int productId) {
        stockLock.lock();
        try{
            String sql = "UPDATE products SET is_deleted = TRUE WHERE product_id = ?";
            try (Connection conn=dbManager.getConnection();
                 PreparedStatement pstmt=conn.prepareStatement(sql)){
                pstmt.setInt(1, productId);
                int affectedRows = pstmt.executeUpdate();
                if (affectedRows>0){
                    Product product=products.get(productId);
                    if(product!=null){
                        product.setDeleted(true);
                        LogManager.getInstance().logInfo(
                                -1,
                                null,
                                String.format("Silinen Urun: %s (ID: %d)",
                                        product.getProductName(),
                                        productId)
                        );
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
                LogManager.getInstance().logError(
                        -1,
                        null,
                        "Urun silinemedi: " + e.getMessage()
                );
            }
        }
        finally{
            stockLock.unlock();
        }
    }
}