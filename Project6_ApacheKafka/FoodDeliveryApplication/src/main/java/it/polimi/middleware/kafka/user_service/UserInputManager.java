package it.polimi.middleware.kafka.user_service;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import it.polimi.middleware.kafka.StringUtils;
import it.polimi.middleware.kafka.beans.Order;
import it.polimi.middleware.kafka.beans.User;
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
import java.util.ArrayList;
import java.util.Map;
import java.util.Properties;

public class UserInputManager extends Thread{

    private static final String usersTopic      = "users_info";
    private static final String ordersTopic     = "orders";

    private final int httpPort                  = 8600;
    private final String httpServicePath        = "/userService";
    private final String httpLoginPath          = "/userLogin";

    private static Map<String, String> db_users;
    private static Map<String, String> db_orders;

    private static KafkaProducer<String, String> producer = null;

    private static int orderCounter = 0;

    public UserInputManager(Map<String, String> db_users, Map<String, String> db_orders, int orderCounter){
        UserInputManager.db_users   = db_users;
        UserInputManager.db_orders  = db_orders;
        producer = setProducer();
        UserInputManager.orderCounter = orderCounter;
    }

    public void run() {
        HttpServer server = null;
        try {
            server = HttpServer.create(new InetSocketAddress(StringUtils.httpServers, httpPort), 0);
            HttpContext context = server.createContext(httpServicePath);
            context.setHandler(UserInputManager::handleRequestService);
            HttpContext contextLogin = server.createContext(httpLoginPath);
            contextLogin.setHandler(UserInputManager::handleRequestLogin);
            server.start();
            System.out.println("UserService is up at " + StringUtils.httpServers + ":" + httpPort + httpServicePath);
            System.out.println("LoginService is up at " + StringUtils.httpServers + ":" + httpPort + httpLoginPath);
        } catch (IOException e) {
            System.out.println("ERROR - unable to start user service on port 8600. closing\n");
        }

    }

    /*************************           HTTP UTILITIES          *********************************/

    private static void handleRequestService(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        // post a new order
        if("POST".equals(exchange.getRequestMethod())) {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(exchange.getRequestBody()));
            StringBuilder stringBuilder = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                stringBuilder.append(line);
            }
            try {
                Gson gson = new Gson();
                Order order = gson.fromJson(stringBuilder.toString(), Order.class);
                order.setId(++orderCounter);
                order.setStatus(OrderStatus.SUBMITTED);
                publishNewOrder(producer, String.valueOf(orderCounter), gson.toJson(order));
                System.out.println(order);
                exchange.sendResponseHeaders(200, 0);
                exchange.getResponseBody().close();
            }catch (Exception e){
                exchange.sendResponseHeaders(400, 0);
                exchange.getResponseBody().close();
            }
        }
        // get all orders (?username=user) or get the status of the order (?username=user&orderId=x)
        else if ("GET".equals(exchange.getRequestMethod())){
            try {
                String[] request = exchange.getRequestURI().getQuery().split("&");
                String username = request[0].split("=")[1];
                Gson gson = new Gson();
                if (request.length == 1) { // all the orders (i have only username)
                    ArrayList<String> userOrders = new ArrayList<>();
                    for (String order: db_orders.values()){
                        if (gson.fromJson(order, Order.class).getUsername().equals(username))
                            userOrders.add(order);
                    }
                    String response = String.valueOf(userOrders);
                    exchange.sendResponseHeaders(200, response.getBytes().length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(response.getBytes());
                    os.close();
                }
                else {
                    String orderJson = db_orders.get(request[1].split("=")[1]);
                    if (orderJson != null && gson.fromJson(orderJson, Order.class).getUsername().equals(username)) {
                        exchange.sendResponseHeaders(200, orderJson.getBytes().length);
                        OutputStream os = exchange.getResponseBody();
                        os.write(orderJson.getBytes());
                        os.close();
                    } else {
                        exchange.sendResponseHeaders(400, 0);
                        exchange.getResponseBody().close();
                    }
                }
            } catch (Exception e){
                exchange.sendResponseHeaders(400, 0);
                exchange.getResponseBody().close();
            }
        }


    }

    // handles both user login and registration
    private static void handleRequestLogin(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        // Signup
        if ("POST".equals(exchange.getRequestMethod())) {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(exchange.getRequestBody()));
            StringBuilder stringBuilder = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                stringBuilder.append(line);
            }
            Gson gson = new Gson();
            try {
                String[] params = stringBuilder.toString().split("&");
                if (params.length != 3)
                    throw new Exception();
                User myUser = new User();
                myUser.setUsername(params[0].split("=")[1]);
                myUser.setPassword(params[1].split("=")[1]);
                myUser.setAddress(params[2].split("=")[1]);
                // if the user doesn't exists, add it
                if (db_users.get(myUser.getUsername()) == null) {
                    publishNewUser(producer, myUser.getUsername(), gson.toJson(myUser));
                    exchange.sendResponseHeaders(200, 0);
                }
                else
                    exchange.sendResponseHeaders(401, 0);

                exchange.getResponseBody().close();
            }
            catch(Exception e){
                exchange.sendResponseHeaders(400, 0);
                exchange.getResponseBody().close();
            }
        }
        // Login
        else if ("GET".equals(exchange.getRequestMethod())) {
            try {
                String[] credentials = exchange.getRequestURI().getQuery().split("&");
                String username = credentials[0].split("=")[1];
                String password = credentials[1].split("=")[1];

                if (checkLogin(username, password))
                    exchange.sendResponseHeaders(200, 0);
                else
                    exchange.sendResponseHeaders(401, 0);

                exchange.getResponseBody().close();
            } catch (Exception e){
                exchange.sendResponseHeaders(401, 0);
                exchange.getResponseBody().close();
            }
        }

    }

    private static boolean checkLogin(String usr, String pwd){
        String userJson = db_users.get(usr);
        if (userJson != null){
            Gson gson = new Gson();
            User user = gson.fromJson(userJson, User.class);
            return pwd.equals(user.getPassword());
        }
        return false;

    }


    /************************         KAFKA PUBLISH FUNCTIONS        ********************************/
    private static void publishNewOrder(KafkaProducer<String, String> orderProducer, String id, String jsonOrder){
        final ProducerRecord<String, String> record = new ProducerRecord<>(ordersTopic, id, jsonOrder);
        orderProducer.send(record);
        db_orders.put(id, jsonOrder);
        System.out.println("Order #" + id + " added successfully");
    }

    private static void publishNewUser(KafkaProducer<String, String> backupProducer, String username, String userJson){
        final ProducerRecord<String, String> record = new ProducerRecord<>(usersTopic, username, userJson);
        backupProducer.send(record);
        db_users.put(username, userJson);
        System.out.println("User " + username + " registered");
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
