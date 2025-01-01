import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LogManager{
    private static LogManager instance;
    private final DatabaseManager dbManager;
    private final BlockingQueue<LogEntry> logQueue;
    private final ExecutorService logProcessor;

    private static class LogEntry{
        final int customerId;
        final Integer orderId;
        final String logType;
        final String details;
        LogEntry(int customerId,Integer orderId,String logType,String details){
            this.customerId =customerId;
            this.orderId =orderId;
            this.logType =logType;
            this.details =details;}
    }

    private LogManager(){ //Bunu bir çeşit singleton pattern olarak düşündüm.
        this.dbManager =DatabaseManager.getInstance();
        this.logQueue =new LinkedBlockingQueue<>();
        this.logProcessor =Executors.newSingleThreadExecutor();
        startLogProcessing();
    }

    public static synchronized LogManager getInstance() {
        if (instance ==null) {
            instance =new LogManager();
        }
        return instance;
    }

    private void startLogProcessing() {
        logProcessor.submit(() ->{
            while (!Thread.currentThread().isInterrupted()) {
                try{
                    LogEntry entry= logQueue.take();
                    dbManager.createLog(
                            entry.customerId,
                            entry.orderId,
                            entry.logType,
                            entry.details
                    );}
                catch (InterruptedException e){
                    Thread.currentThread().interrupt();
                    break;}
            }
        });
    }

    public void logInfo(int customerId, Integer orderId, String message) {
        logQueue.offer(new LogEntry(customerId, orderId, "INFO", message));
        System.out.println("INFO: " + message + " (Musteri: " + customerId +
                (orderId != null ? ", Siparis: " + orderId : "") + ")");
    }

    public void logError(int customerId, Integer orderId, String message) {
        logQueue.offer(new LogEntry(customerId, orderId, "ERROR", message));
        System.err.println("ERROR: " + message+ " (Musteri: " + customerId +
                (orderId != null ? ", Siprais: "+ orderId: "")+ ")");
    }

    public void shutdown() {
        logProcessor.shutdown();
    }
}