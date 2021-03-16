package it.polimi.middleware.kafka.order_service;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import it.polimi.middleware.kafka.StringUtils;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Properties;

public class OrderInputManager extends Thread {

    private static final String itemsTopic = "items_count";

    private final int port              = 8500;
    private final String servicePath    = "/orderService";

    private static Map<String, String> db_items;
    private static KafkaProducer<String, String> itemProducer = null;

    public OrderInputManager(Map<String, String> db_items){
        OrderInputManager.db_items = db_items;
        itemProducer = setItemsProducer();
    }

    @Override
    public void run() {
        HttpServer server = null;
        try {
            server = HttpServer.create(new InetSocketAddress(StringUtils.httpServers, port), 0);
            HttpContext context = server.createContext(servicePath);
            context.setHandler(OrderInputManager::handleRequest);
            server.start();
            System.out.println("OrderService is up at " + StringUtils.httpServers + ":" + port + servicePath);
        } catch (IOException e) {
            System.out.println("ERROR - unable to start OrderService server on port 8500");
        }
    }

    /*************************           HTTP UTILITIES          *********************************/
    private static void handleRequest(HttpExchange exchange) throws IOException{
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        if ("POST".equals(exchange.getRequestMethod())){
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(exchange.getRequestBody()));
            StringBuilder stringBuilder = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                stringBuilder.append(line);
            }
            try {
                String[] input = stringBuilder.toString().split("&");
                // before publish check all items validity
                boolean isValid = true;
                for (String item : input) {
                    String[] item_qty = item.split("=");
                    if (!item_qty[1].matches("\\d+")) {
                        isValid = false;
                        break;
                    }
                }
                if (isValid) {
                    for (String item : input) {
                        String[] item_qty = item.split("=");
                        publishNewItem(itemProducer, item_qty[0], item_qty[1]);
                    }
                    exchange.sendResponseHeaders(200, 0);
                } else exchange.sendResponseHeaders(400, 0);
                exchange.getResponseBody().close();
            } catch (Exception e){
                exchange.sendResponseHeaders(400, 0);
                exchange.getResponseBody().close();
            }
        } else if("GET".equals(exchange.getRequestMethod())){
            String response = (new Gson()).toJson(db_items);
            exchange.sendResponseHeaders(200, response.getBytes().length);
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
        else{
            exchange.sendResponseHeaders(501, 0);
            exchange.getResponseBody().close();
        }
    }


    /************************         KAFKA PUBLISH FUNCTIONS        ********************************/
    private static void publishNewItem(KafkaProducer<String, String> itemProducer, String item, String quantity){
        final ProducerRecord<String, String> record = new ProducerRecord<>(itemsTopic, item, quantity);
        db_items.put(item, quantity);
        itemProducer.send(record);

        System.out.println("\nNew values updated: ");
        db_items.forEach((key1, value1) -> System.out.println(key1 + " -> " + value1));
    }

    /************************         KAFKA CONFIGURATIONS        ********************************/
    private KafkaProducer<String, String> setItemsProducer(){
        // Producer for publishing on items_count topic
        final Properties orderProducerProps = new Properties();
        orderProducerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, StringUtils.brokersAddr);
        orderProducerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        orderProducerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        //producerProps.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, producerTransactionalId);
        // Idempotence = exactly once semantics between the producer and the partition
        //orderProducerProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, String.valueOf(true));
        orderProducerProps.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, String.valueOf(1000));
        return new KafkaProducer<>(orderProducerProps);
    }

}
