package it.polimi.middleware.kafka.shipping_service;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import it.polimi.middleware.kafka.StringUtils;
import it.polimi.middleware.kafka.beans.Order;
import it.polimi.middleware.kafka.enums.OrderStatus;
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

public class ShippingInputManager extends Thread{


    private static final String ordersTopic = "orders";

    private final int httpPort                  = 8400;
    private final String httpServicePath        = "/shippingService";

    private static Map<String, String> db_orders;
    private static KafkaProducer<String, String> orderProducer = null;

    public ShippingInputManager(Map<String, String> db_orders){
        ShippingInputManager.db_orders      = db_orders;
        ShippingInputManager.orderProducer  = setProducer();
    }

    public void run(){
        HttpServer server = null;
        try {
            server = HttpServer.create(new InetSocketAddress(StringUtils.httpServers, httpPort), 0);
            HttpContext context = server.createContext(httpServicePath);
            context.setHandler(ShippingInputManager::handleRequest);
            server.start();
            System.out.println("ShippingService is up at " + StringUtils.httpServers + ":" + httpPort + httpServicePath);
        } catch (IOException e) {
            System.out.println("ERROR - unable to start shipping service on port 8400. closing\n");
        }
    }

    /*************************           HTTP UTILITIES          *********************************/
    private static void handleRequest(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        if("POST".equals(exchange.getRequestMethod())) {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(exchange.getRequestBody()));
            StringBuilder stringBuilder = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                stringBuilder.append(line);
            }
            try {
                String orderId = stringBuilder.toString().split("=")[1];
                if (!orderId.matches("\\d+")) {
                    exchange.sendResponseHeaders(400, 0);
                }
                else {
                    Order myOrder = (new Gson()).fromJson(db_orders.get(orderId), Order.class);
                    if (myOrder != null && myOrder.getStatus() == OrderStatus.DELIVERING) {
                        myOrder.setStatus(OrderStatus.DELIVERED);
                        publishOrder(orderProducer, orderId, (new Gson()).toJson(myOrder));
                        exchange.sendResponseHeaders(200, 0);
                    } else {
                        exchange.sendResponseHeaders(400, 0);
                    }
                }
                exchange.getResponseBody().close();
            } catch (Exception e){
                exchange.sendResponseHeaders(400, 0);
                exchange.getResponseBody().close();
            }
        }
        else if ("GET".equals(exchange.getRequestMethod())){ // Show all orders
            String response = String.valueOf(db_orders.values());
            exchange.sendResponseHeaders(200, response.getBytes().length);
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
        else {
            exchange.sendResponseHeaders(501, 0);
            exchange.getResponseBody().close();
        }
    }

    /************************         KAFKA PUBLISH FUNCTIONS        ********************************/
    private static void publishOrder(KafkaProducer<String, String> orderProducer, String id, String jsonOrder){
        final ProducerRecord<String, String> record = new ProducerRecord<>(ordersTopic, id, jsonOrder);
        orderProducer.send(record);
        db_orders.remove(id);
    }

    /************************         KAFKA CONFIGURATIONS        ********************************/
    private KafkaProducer<String, String> setProducer(){
        // Producer for publishing on backup topic
        final Properties userProducerProps = new Properties();
        userProducerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, StringUtils.brokersAddr);
        userProducerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        userProducerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        userProducerProps.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, String.valueOf(1000));
        return new KafkaProducer<>(userProducerProps);
    }
}
