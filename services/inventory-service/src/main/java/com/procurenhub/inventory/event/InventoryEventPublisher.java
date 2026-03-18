package com.procurehub.inventory.event;

import com.procurehub.inventory.avro.InventoryReservedEvent;
import org.apache.avro.specific.SpecificRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
public class InventoryEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventPublisher.class);

    private final KafkaTemplate<String, SpecificRecord> kafkaTemplate;
    private final String inventoryReservedTopic;

    public InventoryEventPublisher(
            KafkaTemplate<String, SpecificRecord> kafkaTemplate,
            @Value("${app.kafka.topics.inventory-reserved}") String inventoryReservedTopic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.inventoryReservedTopic = inventoryReservedTopic;
    }

    public void publishInventoryReserved(
            long orderId,
            long productId,
            int quantity,
            int availableQuantity,
            int reservedQuantity,
            String message
    ) {
        if (orderId <= 0) {
            log.debug("Skip inventory.reserved publish: orderId={}", orderId);
            return;
        }

        InventoryReservedEvent.Builder builder = InventoryReservedEvent.newBuilder()
                .setOrderId(orderId)
                .setProductId(productId)
                .setQuantity(quantity)
                .setAvailableQuantity(availableQuantity)
                .setReservedQuantity(reservedQuantity)
                .setReservedAt(LocalDateTime.now().toString());

        if (message != null && !message.isBlank()) {
            builder.setMessage(message);
        }

        InventoryReservedEvent event = builder.build();

        try {
            kafkaTemplate.send(inventoryReservedTopic, String.valueOf(orderId), event).get(5, TimeUnit.SECONDS);
            log.info("Published inventory.reserved for orderId={}, productId={}", orderId, productId);
        } catch (Exception e) {
            log.error("Failed to publish inventory.reserved for orderId={}", orderId, e);
        }
    }
}
