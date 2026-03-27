package com.procurehub.notification.kafka;

import com.procurehub.notification.service.NotificationDispatchService;
import com.procurehub.order.avro.OrderCreatedEvent;
import com.procurehub.order.avro.OrderStatusChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventsListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventsListener.class);

    private final NotificationDispatchService notificationDispatchService;

    public OrderEventsListener(NotificationDispatchService notificationDispatchService) {
        this.notificationDispatchService = notificationDispatchService;
    }

    @KafkaListener(topics = "${app.kafka.topics.order-created}", groupId = "${spring.kafka.consumer.group-id}")
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("Consumed order.created for orderId={}, userId={}", event.getOrderId(), event.getUserId());
        notificationDispatchService.handleOrderCreated(event);
    }

    @KafkaListener(topics = "${app.kafka.topics.order-status-changed}", groupId = "${spring.kafka.consumer.group-id}")
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        log.info(
                "Consumed order.status-changed for orderId={}, {} -> {}",
                event.getOrderId(),
                event.getOldStatus(),
                event.getNewStatus()
        );
        notificationDispatchService.handleOrderStatusChanged(event);
    }
}
