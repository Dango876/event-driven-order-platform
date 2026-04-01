package com.procurehub.inventory.kafka;

import com.procurehub.inventory.service.OrderCreatedReservationService;
import com.procurehub.order.avro.OrderCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderCreatedEventsListener {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedEventsListener.class);

    private final OrderCreatedReservationService orderCreatedReservationService;

    public OrderCreatedEventsListener(OrderCreatedReservationService orderCreatedReservationService) {
        this.orderCreatedReservationService = orderCreatedReservationService;
    }

    @KafkaListener(topics = "${app.kafka.topics.order-created}", groupId = "${spring.kafka.consumer.group-id}")
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info(
                "Inventory-service consumed order.created: orderId={}, productId={}, quantity={}",
                event.getOrderId(),
                event.getProductId(),
                event.getQuantity()
        );
        orderCreatedReservationService.handle(event);
    }
}
