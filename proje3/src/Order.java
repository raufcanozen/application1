import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class Order {
    private int orderId;
    private int customerId;
    private Map<Integer,Integer> productQuantities;
    private double totalPrice;
    private LocalDateTime orderDate;
    private OrderStatus status;

    public enum OrderStatus{
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED
    }

    public Order(int orderId, int customerId) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.productQuantities = new HashMap<>();
        this.orderDate = LocalDateTime.now();
        this.status = OrderStatus.PENDING;
        this.totalPrice = 0.0;
    }

    public int getOrderId() {
        return orderId;
    }

    public void setOrderId(int orderId) {
        this.orderId = orderId;
    }

    public int getCustomerId() {
        return customerId;
    }

    public Map<Integer, Integer> getProductQuantities() {
        return new HashMap<>(productQuantities);
    }

    public void addProduct(int productId, int quantity) {
        productQuantities.put(productId, quantity);
    }

    public double getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(double totalPrice) {
        this.totalPrice = totalPrice;
    }

    public LocalDateTime getOrderDate() {
        return orderDate;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }
}