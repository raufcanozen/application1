import java.util.List;
import java.util.Random;

public class CustomerInitializer {
    private final DatabaseManager dbManager;
    private final Random random;

    public CustomerInitializer() {
        this.dbManager =DatabaseManager.getInstance();
        this.random =new Random();
    }

    public void initializeCustomers() {
        if (dbManager.customersExist()) {
            LogManager.getInstance().logInfo(
                    -1,
                    null,
                    "Musteriler Databaseden Yukleniyor");

            List<Customer> varolan_musteriler= dbManager.getAllCustomers();
            OrderManager orderManager = OrderManager.getInstance();
            for(int i= 0 ;i< varolan_musteriler.size(); i++){
                Customer customer =varolan_musteriler.get(i);
                orderManager.addCustomer(customer);
            }


            LogManager.getInstance().logInfo(
                    -1,
                    null,
                    String.format("%d adet musteri yuklendi", varolan_musteriler.size()));
            return;
        }

        //Eger sistemde musteri yoksa sıfırdan yarattık.
        OrderManager orderManager= OrderManager.getInstance();
        int numberOfCustomers= random.nextInt(6) +5;
        int premiumCount = 0;

        for (int i= 1; i <=numberOfCustomers; i++) {
            double budget= 500+ random.nextDouble()* 2500; //500 ile 3000 lira arasında budget belirledik.

            Customer.CustomerType type;
            if (premiumCount < 2){ // En az 2 adet premium musterinin olduğundan emin olduk
                type = Customer.CustomerType.PREMIUM;
                premiumCount++;
            }
            else {
                if (random.nextBoolean()){ //E
                    type = Customer.CustomerType.PREMIUM;
                    premiumCount++;}
                else{
                    type = Customer.CustomerType.STANDARD;}
            }

            String customerName = "Musteri"+ i;
            Customer customer= dbManager.createCustomer(customerName ,budget ,type);

            if(customer!=null){
                orderManager.addCustomer(customer);
                LogManager.getInstance().logInfo(
                        customer.getCustomerId(),
                        null,
                        String.format("%s adli musteri %.2f TL ile oluşturuldu",
                                type.toString(), budget));
            }
        }

        LogManager.getInstance().logInfo(
                -1,
                null,
                String.format("%d adet musteri olusturuldu, bunların %d si premium",
                        numberOfCustomers, premiumCount));
    }
}