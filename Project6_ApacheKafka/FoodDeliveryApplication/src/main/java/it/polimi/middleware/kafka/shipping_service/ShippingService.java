package it.polimi.middleware.kafka.shipping_service;

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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class ShippingService {

    private static final String ordersTopic             = "orders";
    private static final String shippingGroup           = "shippingGroup";
    private static final String offsetResetStrategy     = "earliest";
    private static final String isolationLevelStrategy  = "read_committed";

    private static Map<String, String> db_orders;

    public static void main(String[] args) {
        db_orders = Collections.synchronizedMap(new HashMap<>());
        final KafkaConsumer<String, String> selfConsumer = setSelfConsumer();
        recoverShippingOrderState(selfConsumer);
        selfConsumer.close();

        ShippingInputManager inputManager = new ShippingInputManager(db_orders);
        inputManager.start();

        ShippingConsumerProducer consumerProducer = new ShippingConsumerProducer(db_orders);
        consumerProducer.start();

    }

    /************************         KAFKA RECOVERY UTILITIES        ********************************/
    private static void recoverShippingOrderState(KafkaConsumer<String, String> selfConsumer){
        selfConsumer.subscribe(Collections.singletonList(ordersTopic));

        final ConsumerRecords<String, String> records = selfConsumer.poll(Duration.of(10, ChronoUnit.SECONDS));
        selfConsumer.seekToBeginning(records.partitions());
        for(final ConsumerRecord<String, String> record : records){
            Order tmpOrder = (new Gson()).fromJson(record.value(), Order.class);
            switch (tmpOrder.getStatus()){
                case ACCEPTED:
                case DELIVERING:
                    db_orders.put(record.key(), record.value());
                    break;
                case REFUSED:
                case INVALID:
                case DELIVERED:
                    db_orders.remove(record.key());
                    break;
                default:
                    // do nothing
            }
        }
        if (db_orders.isEmpty())
            System.out.println("No shipping orders found");
        else {
            System.out.println(">>> CACHED SHIPPING ORDERS <<<");
            db_orders.forEach((key, value) -> System.out.println("Order Id: " + key + ", Order Status: " + value));
        }
    }

    /************************         KAFKA CONFIGURATIONS        ********************************/
    private static KafkaConsumer<String, String> setSelfConsumer(){
        //Self consumer
        final Properties selfConsumerProps = new Properties();
        selfConsumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, StringUtils.brokersAddr);
        selfConsumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, shippingGroup);
        selfConsumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, offsetResetStrategy);
        selfConsumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        selfConsumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        selfConsumerProps.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, isolationLevelStrategy);
        return new KafkaConsumer<>(selfConsumerProps);
    }
}
