public class StockManagementApplication {
    private final OrderManager orderManager;
    private final StockManager stockManager;
    private final LogManager logManager;
    private final MainUI mainUI;
    private final CustomerInitializer customerInitializer;

    public StockManagementApplication() {
        this.orderManager = OrderManager.getInstance();
        this.stockManager = StockManager.getInstance();
        this.logManager = LogManager.getInstance();
        this.customerInitializer = new CustomerInitializer();
        this.mainUI = new MainUI();
    }

    public void start() {
        //Random sayıda müşteri yarattım.
        customerInitializer.initializeCustomers();

        // Sipariş işlemlerini başlattım
        orderManager.startOrderProcessing();

        //UI'ı gösteridm
        javax.swing.SwingUtilities.invokeLater(() -> {
            mainUI.setVisible(true);
        });

        //Kapatmak için bunu ekledim.
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
    }

    private void shutdown() {
        orderManager.shutdown();
        logManager.shutdown();
        mainUI.shutdown();
    }

    public static void main(String[] args) {
        StockManagementApplication app = new StockManagementApplication();
        app.start();
    }
}