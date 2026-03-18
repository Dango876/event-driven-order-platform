package com.procurehub.order.event;

import com.procurehub.order.avro.OrderCreatedEvent;
import com.procurehub.order.avro.OrderStatusChangedEvent;
import com.procurehub.order.model.Order;
import com.procurehub.order.model.OrderStatus;
import org.apache.avro.specific.SpecificRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class OrderEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private final KafkaTemplate<String, SpecificRecord> kafkaTemplate;
    private final String orderCreatedTopic;
    private final String orderStatusChangedTopic;

    public OrderEventPublisher(
            KafkaTemplate<String, SpecificRecord> kafkaTemplate,
            @Value("${app.kafka.topics.order-created}") String orderCreatedTopic,
            @Value("${app.kafka.topics.order-status-changed}") String orderStatusChangedTopic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.orderCreatedTopic = orderCreatedTopic;
        this.orderStatusChangedTopic = orderStatusChangedTopic;
    }

    public void publishOrderCreated(Order order) {
        OrderCreatedEvent event = OrderCreatedEvent.newBuilder()
                .setOrderId(order.getId())
                .setUserId(order.getUserId())
                .setProductId(order.getProductId())
                .setQuantity(order.getQuantity())
                .setStatus(order.getStatus().name())
                .setStatusMessage(order.getStatusMessage())
                .setCreatedAt(order.getCreatedAt().toString())
                .build();

        send(orderCreatedTopic, String.valueOf(order.getId()), event, "order.created");
    }

    public void publishOrderStatusChanged(Order order, OrderStatus oldStatus) {
        OrderStatusChangedEvent event = OrderStatusChangedEvent.newBuilder()
                .setOrderId(order.getId())
                .setOldStatus(oldStatus.name())
                .setNewStatus(order.getStatus().name())
                .setStatusMessage(order.getStatusMessage())
                .setUpdatedAt(order.getUpdatedAt().toString())
                .build();

        send(orderStatusChangedTopic, String.valueOf(order.getId()), event, "order.status-changed");
    }

    private void send(String topic, String key, SpecificRecord event, String eventName) {
        try {
            kafkaTemplate.send(topic, key, event).get(5, TimeUnit.SECONDS);
            log.info("Published {} for orderId={}", eventName, key);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to publish " + eventName, e);
        }
    }
}
