package com.procurehub.order.kafka;

import com.procurehub.inventory.avro.InventoryReservationFailedEvent;
import com.procurehub.inventory.avro.InventoryReservedEvent;
import com.procurehub.order.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class InventoryEventsListener {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventsListener.class);

    private final OrderService orderService;

    public InventoryEventsListener(OrderService orderService) {
        this.orderService = orderService;
    }

    @KafkaListener(topics = "${app.kafka.topics.inventory-reserved}", groupId = "${spring.kafka.consumer.group-id}")
    public void onInventoryReserved(InventoryReservedEvent event) {
        log.info(
                "Order-service consumed inventory.reserved: orderId={}, productId={}, quantity={}, reservedQuantity={}",
                event.getOrderId(),
                event.getProductId(),
                event.getQuantity(),
                event.getReservedQuantity()
        );

        orderService.handleInventoryReserved(event.getOrderId(), event.getMessage());
    }

    @KafkaListener(topics = "${app.kafka.topics.inventory-reserve-failed}", groupId = "${spring.kafka.consumer.group-id}")
    public void onInventoryReservationFailed(InventoryReservationFailedEvent event) {
        log.info(
                "Order-service consumed inventory.reserve-failed: orderId={}, productId={}, quantity={}, message={}",
                event.getOrderId(),
                event.getProductId(),
                event.getQuantity(),
                event.getMessage()
        );

        orderService.handleInventoryReservationFailed(event.getOrderId(), event.getMessage());
    }
}
