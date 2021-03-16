package it.polimi.middleware.kafka.user_service;

import it.polimi.middleware.kafka.StringUtils;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class UserService {

    private static final String usersTopic      = "users_info";
    private static final String usersGroup      = "usersGroup";
    private static final String ordersTopic     = "orders";

    private static final String offsetResetStrategy     = "earliest";
    private static final String isolationLevelStrategy  = "read_committed";


    private static Map<String, String> db_users;
    private static Map<String, String> db_orders;
    private static int orderCounter = 0;


    public static void main(String[] args) {
        db_users = Collections.synchronizedMap(new HashMap<>());
        db_orders = Collections.synchronizedMap(new HashMap<>());

        final KafkaConsumer<String, String> selfConsumer = setSelfConsumer();
        recoverUsersState(selfConsumer);
        recoverOrdersState(selfConsumer);
        selfConsumer.close();

        UserInputManager input = new UserInputManager(db_users, db_orders, orderCounter);
        input.start();

        UserConsumer consumer = new UserConsumer(db_orders);
        consumer.start();
    }

    /************************         KAFKA RECOVERY UTILITIES        ********************************/
    private static void recoverUsersState(KafkaConsumer<String, String> selfConsumer){
        // Recover the state of users
        selfConsumer.subscribe(Collections.singletonList(usersTopic));
        if (db_users.isEmpty()){
            final ConsumerRecords<String, String> records = selfConsumer.poll(Duration.of(10, ChronoUnit.SECONDS));
            selfConsumer.seekToBeginning(records.partitions());
            for(final ConsumerRecord<String, String> record : records){
                db_users.put(record.key(), record.value());
            }
            if (db_users.isEmpty())
                System.out.println("No users found");
            else {
                System.out.println(">>> CACHED USERS <<<");
                db_users.forEach((key, value) -> System.out.println("User: " + key + ", " + value));
            }
            System.out.println();
        }
        selfConsumer.unsubscribe();
    }

    private static void recoverOrdersState(KafkaConsumer<String, String> selfConsumer){
        // Recover the state of orders
        selfConsumer.subscribe(Collections.singletonList(ordersTopic));
        if (db_orders.isEmpty()){
            final ConsumerRecords<String, String> records = selfConsumer.poll(Duration.of(10, ChronoUnit.SECONDS));
            selfConsumer.seekToBeginning(records.partitions());
            for(final ConsumerRecord<String, String> record : records){
                db_orders.put(record.key(), record.value());
                orderCounter++;
            }
            if (db_orders.isEmpty())
                System.out.println("No orders found");
            else {
                System.out.println(">>> CACHED ORDERS <<<");
                db_orders.forEach((key, value) -> System.out.println("Order: #" + key));
            }
            System.out.println();
        }
        selfConsumer.unsubscribe();
    }

    /************************         KAFKA CONFIGURATIONS        ********************************/
    private static KafkaConsumer<String, String> setSelfConsumer(){
        //Self consumer
        final Properties selfConsumerProps = new Properties();
        selfConsumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, StringUtils.brokersAddr);
        selfConsumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, usersGroup);
        selfConsumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, offsetResetStrategy);
        selfConsumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        selfConsumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        selfConsumerProps.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, isolationLevelStrategy);
        return new KafkaConsumer<>(selfConsumerProps);
    }
}
