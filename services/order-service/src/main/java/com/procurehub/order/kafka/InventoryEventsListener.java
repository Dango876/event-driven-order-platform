package com.procurehub.order.kafka;

import com.procurehub.inventory.avro.InventoryReservedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class InventoryEventsListener {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventsListener.class);

    @KafkaListener(topics = "${app.kafka.topics.inventory-reserved}", groupId = "${spring.kafka.consumer.group-id}")
    public void onInventoryReserved(InventoryReservedEvent event) {
        log.info(
                "Order-service consumed inventory.reserved: orderId={}, productId={}, quantity={}, reservedQuantity={}",
                event.getOrderId(),
                event.getProductId(),
                event.getQuantity(),
                event.getReservedQuantity()
        );
    }
}
