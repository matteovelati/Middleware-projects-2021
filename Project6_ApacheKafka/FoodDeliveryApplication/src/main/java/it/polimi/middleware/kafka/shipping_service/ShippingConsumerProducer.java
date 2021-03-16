package it.polimi.middleware.kafka.shipping_service;

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

public class ShippingConsumerProducer extends Thread {

    private static final String ordersTopic         = "orders";
    private static final String ordersGroup         = "groupOrdersShipping";
    private static final String transactionalId     = "shipping_transactionId";

    // latest for real-time purpose
    private static final String offsetResetStrategy     = "latest";
    private static final String isolationLevelStrategy  = "read_committed";

    private Map<String, String> db_orders;

    public ShippingConsumerProducer(Map<String, String> db_orders){
        this.db_orders = db_orders;
    }

    public void run(){

        final KafkaConsumer<String, String> orderConsumer = setOrderConsumer();
        final KafkaProducer<String, String> atomicProducer = setProducer();

        // auto-commit disabled: first consume then produce atomically in the same transaction
        atomicProducer.initTransactions();
        orderConsumer.subscribe(Collections.singletonList(ordersTopic));

        while (true) {
            final ConsumerRecords<String, String> records = orderConsumer.poll(Duration.of(5, ChronoUnit.MINUTES));
            for(final ConsumerRecord<String, String> record : records) {

                Order myOrder   = (new Gson()).fromJson(record.value(), Order.class);

                if (myOrder.getStatus() == OrderStatus.ACCEPTED){
                    atomicProducer.beginTransaction();
                    if (isOrderValid(myOrder)){
                        myOrder.setStatus(OrderStatus.DELIVERING);
                        db_orders.put(record.key(), new Gson().toJson(myOrder));
                    }
                    else {
                        myOrder.setStatus(OrderStatus.INVALID);
                        db_orders.remove(record.key());
                    }

                    publishOrderUpdated(atomicProducer, String.valueOf(myOrder.getId()), (new Gson()).toJson(myOrder));

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
                else if (myOrder.getStatus() == OrderStatus.DELIVERED)
                    db_orders.remove(record.key());
            }
        }
    }

    private boolean isOrderValid(Order order){
        if (order.getAddress() == null) return false;
        return (new Random()).nextInt(10) < 7; // 20% false
    }

    /************************         KAFKA PUBLISH FUNCTIONS        ********************************/
    private void publishOrderUpdated(KafkaProducer<String, String> atomicProducer, String orderID, String orderJson){
        final ProducerRecord<String, String> record = new ProducerRecord<>(ordersTopic, orderID, orderJson);
        atomicProducer.send(record);
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
