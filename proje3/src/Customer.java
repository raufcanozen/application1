public class Customer {
    private int customerId;
    private String customerName;
    private double budget;
    private CustomerType customerType;
    private double totalSpent;
    private double priorityScore;
    private long waitingStartTime;
    private boolean isProcessing;

    public void setTotalSpent(double totalSpent) {
        this.totalSpent = totalSpent;
    }

    public enum CustomerType {
        PREMIUM,
        STANDARD
    }

    public Customer(int customerId, String customerName, double budget, CustomerType customerType) {
        this.customerId=customerId;
        this.customerName= customerName;
        this.budget= budget;
        this.customerType= customerType;
        this.totalSpent= 0;
        if (customerType ==CustomerType.PREMIUM) {
            this.priorityScore= 15;
        } else {
            this.priorityScore= 10;
        }
        this.waitingStartTime= 0;
        this.isProcessing= false;
    }

    //Getter ve setterlar.
    public int getCustomerId() {
        return customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public double getBudget() {
        return budget;
    }

    public void setBudget(double budget) {
        this.budget = budget;
    }

    public CustomerType getCustomerType() {
        return customerType;
    }

    public double getTotalSpent() {
        return totalSpent;
    }

    public void addToTotalSpent(double amount) {
        this.totalSpent += amount;
    }

    public double getPriorityScore() {
        return priorityScore;
    }

    public void setPriorityScore(double priorityScore) {
        this.priorityScore = priorityScore;
    }

    public void setWaitingStartTime(long waitingStartTime) {
        this.waitingStartTime = waitingStartTime;
    }

    public boolean isProcessing() {
        return isProcessing;
    }

    public void setProcessing(boolean processing) {
        isProcessing = processing;
    }

    //--------------------------------------------------------------------------------

    public synchronized void updatePriorityScore() {
        if (waitingStartTime== 0) {
            //Eğer ki beklemiyorsa, türlerine göre öncelik
            if (customerType == CustomerType.PREMIUM) {
                this.priorityScore = 15;
            } else {
                this.priorityScore = 10;
            }
            return;
        }

        long suanki_zaman= System.currentTimeMillis();
        double saniye_cinsinden= (suanki_zaman-waitingStartTime) / 1000.0;
        this.priorityScore =priorityScore +(saniye_cinsinden*0.5);
    }

    public synchronized void startWaiting() {
        if (this.waitingStartTime==0){  //Sadece ve sadece bekliyorsa
            this.waitingStartTime = System.currentTimeMillis();
        }
    }

    public synchronized void stopWaiting() {
        this.waitingStartTime=0;
        if (customerType == CustomerType.PREMIUM) {
            this.priorityScore = 15;
        } else {
            this.priorityScore = 10;
        }
    }
}