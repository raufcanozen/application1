public class Product{
    private int productId;
    private String productName;
    private int stock;
    private double price;
    private boolean isDeleted;

    public Product(int productId, String productName, int stock, double price) {
        this.productId=productId;
        this.productName=productName;
        this.stock=stock;
        this.price=price;
        this.isDeleted=false;
    }

    public int getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public synchronized int getStock() {
        return stock;
    }

    public synchronized void setStock(int stock) {
        this.stock = stock;
    }

    public synchronized boolean updateStock(int quantity) {
        if (stock>=quantity){
            stock-=quantity;
            return true;
        }
        return false;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public boolean isDeleted() {
        return isDeleted;
    }

    public void setDeleted(boolean deleted) {
        isDeleted = deleted;}

    public void setProductId(int productId) {
        this.productId = productId;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }
}