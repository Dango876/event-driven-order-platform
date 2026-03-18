package com.procurehub.notification.kafka;

import com.procurehub.order.avro.OrderCreatedEvent;
import com.procurehub.order.avro.OrderStatusChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventsListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventsListener.class);

    @KafkaListener(topics = "${app.kafka.topics.order-created}", groupId = "${spring.kafka.consumer.group-id}")
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("Notification: order created, orderId={}, userId={}, status={}",
                event.getOrderId(), event.getUserId(), event.getStatus());
    }

    @KafkaListener(topics = "${app.kafka.topics.order-status-changed}", groupId = "${spring.kafka.consumer.group-id}")
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        log.info("Notification: order status changed, orderId={}, {} -> {}, message={}",
                event.getOrderId(), event.getOldStatus(), event.getNewStatus(), event.getStatusMessage());
    }
}
