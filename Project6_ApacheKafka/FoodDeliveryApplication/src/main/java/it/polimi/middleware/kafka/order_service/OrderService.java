package it.polimi.middleware.kafka.order_service;

import it.polimi.middleware.kafka.StringUtils;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class OrderService {

    private static final String itemsTopic              = "items_count";
    private static final String itemsGroup              = "groupItems";
    private static final String placedOrdersTopic       = "orders";

    // earliest for recovery purpose
    private static final String offsetResetStrategy     = "earliest";
    private static final String isolationLevelStrategy  = "read_committed";

    private static final Map<String, String> db_items   = Collections.synchronizedMap(new HashMap<>());
    private static final Map<String, String> db_orders  = Collections.synchronizedMap(new HashMap<>());

    public static void main(String[] args) {

        final KafkaConsumer<String, String> selfConsumer    = setSelfConsumer();

        // initialize and recover the elements from kafka topics
        recoverItemsState(selfConsumer);
        recoverOrdersState(selfConsumer);
        selfConsumer.close();

        /*
         * Start the InputManager thread
         * This thread processes ADMIN inputs (actually from CLI, maybe from HTTP POST request).
         * Then publish on itemsTopic the new availability for each item.
         */
        OrderInputManager inputThread   = new OrderInputManager(db_items);
        inputThread.start();

        /*
         * Start the ConsumerProducer thread
         * This thread consumes SUBMITTED orders. It processes them, then publishes to the same topic the
         * order with a new status: REFUSED or ACCEPTED, based on items availability.
         * If the order is accepted, it also publishes the new items availability on itemsTopic.
         */
        OrderConsumerProducer consumerProducerThread = new OrderConsumerProducer(db_orders, db_items);
        consumerProducerThread.start();

    }



    private static void initializeItemsMap(){
        db_items.clear();
        db_items.put("Pizza", "99");
        db_items.put("Hamburger", "99");
        db_items.put("Pasta", "99");
        db_items.put("Chips", "99");
        db_items.put("Coffee", "99");
    }

    /************************         KAFKA RECOVERY UTILITIES        ********************************/
    private static void recoverItemsState(KafkaConsumer<String, String> selfConsumer){
        // Recover the state of items
        selfConsumer.subscribe(Collections.singletonList(itemsTopic));
        if (db_items.isEmpty()){
            initializeItemsMap();

            final ConsumerRecords<String, String> records = selfConsumer.poll(Duration.of(10, ChronoUnit.SECONDS));
            selfConsumer.seekToBeginning(records.partitions());
            for(final ConsumerRecord<String, String> record : records){
                db_items.put(record.key(), record.value());
            }
            if (db_items.isEmpty())
                System.out.println("No items found");
            else {
                System.out.println(">>> CACHED ITEMS <<<");
                db_items.forEach((key, value) -> System.out.println("Item: " + key + ", " + value));
            }
            System.out.println();
        }
        selfConsumer.unsubscribe();
    }

    private static void recoverOrdersState(KafkaConsumer<String, String> selfConsumer){
        // Recover the state of orders
        selfConsumer.subscribe(Collections.singletonList(placedOrdersTopic));
        if (db_orders.isEmpty()){
            final ConsumerRecords<String, String> records = selfConsumer.poll(Duration.of(10, ChronoUnit.SECONDS));
            selfConsumer.seekToBeginning(records.partitions());
            for(final ConsumerRecord<String, String> record : records){
                db_orders.put(record.key(), record.value());
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
        selfConsumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, itemsGroup);
        selfConsumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, offsetResetStrategy);
        selfConsumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        selfConsumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        selfConsumerProps.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, isolationLevelStrategy);
        return new KafkaConsumer<>(selfConsumerProps);
    }

}
