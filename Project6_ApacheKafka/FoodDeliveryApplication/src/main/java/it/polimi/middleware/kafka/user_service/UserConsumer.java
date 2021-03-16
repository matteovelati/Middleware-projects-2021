package it.polimi.middleware.kafka.user_service;

import com.google.gson.Gson;
import it.polimi.middleware.kafka.StringUtils;
import it.polimi.middleware.kafka.beans.Order;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class UserConsumer extends Thread{

    private static final String ordersTopic         = "orders";
    private static final String ordersGroup         = "groupOrdersNotSubmitted";
    private static final int autoCommitIntervalMs   = 15000;

    private final Map<String, String> db_orders;

    public UserConsumer(Map<String, String> db_orders){
        this.db_orders = db_orders;
    }

    public void run(){
        final KafkaConsumer<String, String> orderConsumer   = setOrderConsumer();

        orderConsumer.subscribe(Collections.singleton(ordersTopic));

        while (true){
            final ConsumerRecords<String, String> records = orderConsumer.poll(Duration.of(5, ChronoUnit.MINUTES));
            for (final ConsumerRecord<String, String> record : records){
                Order order = (new Gson()).fromJson(record.value(), Order.class);
                switch (order.getStatus()){
                    case SUBMITTED:
                        // do nothing
                        break;
                    default:
                        db_orders.put(record.key(), record.value());
                }
            }
        }
    }

    /************************         KAFKA CONFIGURATIONS        ********************************/
    private static KafkaConsumer<String, String> setOrderConsumer(){
        //placed order consumer
        final Properties orderConsumerProps = new Properties();
        orderConsumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, StringUtils.brokersAddr);
        orderConsumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, ordersGroup);
        orderConsumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        orderConsumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        orderConsumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        orderConsumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, String.valueOf(true));
        //orderConsumerProps.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, String.valueOf(autoCommitIntervalMs));
        return new KafkaConsumer<>(orderConsumerProps);
    }
}
