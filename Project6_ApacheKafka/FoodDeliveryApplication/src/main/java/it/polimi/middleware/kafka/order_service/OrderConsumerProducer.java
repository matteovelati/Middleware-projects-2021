package it.polimi.middleware.kafka.order_service;

import com.google.gson.Gson;
import it.polimi.middleware.kafka.StringUtils;
import it.polimi.middleware.kafka.beans.Order;
import it.polimi.middleware.kafka.enums.OrderStatus;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class OrderConsumerProducer extends Thread {

    private static final String ordersTopic         = "orders";
    private static final String ordersGroup         = "groupOrdersSubmitted";
    private static final String itemsTopic          = "items_count";
    private static final String transactionalId     = "transactionId";

    // latest for real-time purpose
    private static final String offsetResetStrategy     = "latest";
    private static final String isolationLevelStrategy  = "read_committed";

    private final Map<String, String> db_orders;
    private final Map<String, String> db_items;

    public OrderConsumerProducer(Map<String, String> db_orders, Map<String, String> db_items){
        this.db_orders = db_orders;
        this.db_items  = db_items;
    }

    @Override
    public void run() {

        final KafkaConsumer<String, String> orderConsumer       = setOrderConsumer();
        final KafkaProducer<String, String> atomicProducer      = setProducer();

        // auto-commit disabled: first consume then produce atomically in the same transaction
        atomicProducer.initTransactions();
        orderConsumer.subscribe(Collections.singletonList(ordersTopic));

        while (true) {

            final ConsumerRecords<String, String> records = orderConsumer.poll(Duration.of(5, ChronoUnit.MINUTES));
            for(final ConsumerRecord<String, String> record : records) {
                atomicProducer.beginTransaction();

                Gson gson = new Gson();
                String idOrder  = record.key();
                Order myOrder   = gson.fromJson(record.value(), Order.class);

                if (myOrder.getStatus() == OrderStatus.SUBMITTED) {
                    System.out.println("\nNew order received! ID #" + idOrder + ". List of items requested is");
                    myOrder.getMapItemToQuantity().forEach((key1, value1) -> System.out.println(key1 + " -> " + value1));
                    // check if the items requested are available
                    for (String itemRequested : myOrder.getMapItemToQuantity().keySet()) {
                        String quantityAvailable = db_items.get(itemRequested);
                        // checkAvailability()
                        if (quantityAvailable == null
                                || Integer.parseInt(quantityAvailable) - myOrder.getMapItemToQuantity().get(itemRequested) < 0) {
                            myOrder.setStatus(OrderStatus.REFUSED);
                            System.out.println("Quantity of item " + itemRequested + " is less than requested. \nSorry, order REFUSED");
                            break;
                        }
                    }
                    if (myOrder.getStatus() == OrderStatus.SUBMITTED) {
                        // change availability of each item requested
                        for (String itemRequested : myOrder.getMapItemToQuantity().keySet()) {
                            String quantityAvailable = db_items.get(itemRequested);
                            // changeAvaiability(): local + publish
                            publishItemUpdated(atomicProducer, itemRequested, String.valueOf(
                                    Integer.parseInt(quantityAvailable) - myOrder.getMapItemToQuantity().get(itemRequested)));
                        }
                        myOrder.setStatus(OrderStatus.ACCEPTED);
                        System.out.println("Your order has been ACCEPTED! please wait for delivery updates\n");
                    }

                    publishOrderUpdated(atomicProducer, String.valueOf(myOrder.getId()), gson.toJson(myOrder));
                    producerCommitTransaction(atomicProducer, records);

                }
                else if (myOrder.getStatus() == OrderStatus.INVALID){
                    // reset availability of each item requested
                    for (String itemRequested : myOrder.getMapItemToQuantity().keySet()) {
                        String quantityAvailable = db_items.get(itemRequested);
                        // changeAvaiability(): local + publish
                        publishItemUpdated(atomicProducer, itemRequested, String.valueOf(
                                Integer.parseInt(quantityAvailable) + myOrder.getMapItemToQuantity().get(itemRequested)));
                    }
                    producerCommitTransaction(atomicProducer, records);
                }
                else
                    atomicProducer.abortTransaction();
            }
        }
    }


    /************************         KAFKA PUBLISH FUNCTIONS        ********************************/
    private void publishItemUpdated(KafkaProducer<String, String> atomicProducer, String item, String value){
        final ProducerRecord<String, String> record = new ProducerRecord<>(itemsTopic, item, value);
        db_items.put(item, value);
        atomicProducer.send(record);

        System.out.println("\nUpdated values: ");
        db_items.forEach((key1, value1) -> System.out.println(key1 + " -> " + value1));
    }

    private void publishOrderUpdated(KafkaProducer<String, String> atomicProducer, String orderID, String orderJson){
        final ProducerRecord<String, String> record = new ProducerRecord<>(ordersTopic, orderID, orderJson);
        db_orders.put(orderID, orderJson);
        atomicProducer.send(record);

        System.out.println("Order #" + orderID + " updated successfully");
    }

    private void producerCommitTransaction(KafkaProducer<String, String> atomicProducer, ConsumerRecords<String, String> records){
        // The producer manually commits the outputs for the consumer within the transaction
        final Map<TopicPartition, OffsetAndMetadata> map = new HashMap<>();
        for (final TopicPartition partition : records.partitions()) {
            final List<ConsumerRecord<String, String>> partitionRecords = records.records(partition);
            final long lastOffset = partitionRecords.get(partitionRecords.size() - 1).offset();
            map.put(partition, new OffsetAndMetadata(lastOffset + 1));
        }
        atomicProducer.sendOffsetsToTransaction(map, ordersGroup);
        atomicProducer.commitTransaction();
    }

    /************************         KAFKA CONFIGURATIONS        ********************************/
    private KafkaProducer<String, String> setProducer(){
        // general kafka producer
        final Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, StringUtils.brokersAddr);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, transactionalId);
        // Idempotence = exactly once semantics between the producer and the partition
        producerProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, String.valueOf(true));
        //producerProps.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, String.valueOf(1000));
        return new KafkaProducer<>(producerProps);
    }

    private KafkaConsumer<String, String> setOrderConsumer(){
        // consumer for topic order:
        final Properties orderConsumerProps = new Properties();
        orderConsumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, StringUtils.brokersAddr);
        orderConsumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, ordersGroup);
        orderConsumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, offsetResetStrategy);
        orderConsumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        orderConsumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        orderConsumerProps.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, isolationLevelStrategy);
        orderConsumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return new KafkaConsumer<>(orderConsumerProps);
    }

}
