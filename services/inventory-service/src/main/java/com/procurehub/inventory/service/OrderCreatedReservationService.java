package com.procurehub.inventory.service;

import com.procurehub.inventory.api.dto.InventoryItemResponse;
import com.procurehub.inventory.event.InventoryEventPublisher;
import com.procurehub.order.avro.OrderCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class OrderCreatedReservationService {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedReservationService.class);

    private static final String DEDUP_KEY_PREFIX = "inventory:order-created:processed:";
    private static final String PROCESSING_STATE = "PROCESSING";
    private static final Duration PROCESSING_TTL = Duration.ofHours(1);
    private static final Duration PROCESSED_TTL = Duration.ofDays(7);

    private final InventoryService inventoryService;
    private final InventoryEventPublisher inventoryEventPublisher;
    private final StringRedisTemplate redisTemplate;

    public OrderCreatedReservationService(
            InventoryService inventoryService,
            InventoryEventPublisher inventoryEventPublisher,
            StringRedisTemplate redisTemplate
    ) {
        this.inventoryService = inventoryService;
        this.inventoryEventPublisher = inventoryEventPublisher;
        this.redisTemplate = redisTemplate;
    }

    public void handle(OrderCreatedEvent event) {
        long orderId = event.getOrderId();
        String key = DEDUP_KEY_PREFIX + orderId;
        boolean markerAcquired = tryStartProcessing(key);

        if (!markerAcquired) {
            log.info("Skip duplicate order.created for orderId={}", orderId);
            return;
        }

        try {
            InventoryItemResponse item = inventoryService.reserve(event.getProductId(), event.getQuantity());
            inventoryEventPublisher.publishInventoryReserved(
                    orderId,
                    item.getProductId(),
                    event.getQuantity(),
                    item.getAvailableQuantity(),
                    item.getReservedQuantity(),
                    "reserved"
            );
            markProcessed(key, "RESERVED");
        } catch (NotEnoughStockException | IllegalArgumentException ex) {
            inventoryEventPublisher.publishInventoryReservationFailed(
                    orderId,
                    event.getProductId(),
                    event.getQuantity(),
                    ex.getMessage()
            );
            markProcessed(key, "FAILED");
        } catch (RuntimeException ex) {
            clearMarker(key, markerAcquired);
            throw ex;
        }
    }

    private boolean tryStartProcessing(String key) {
        try {
            ValueOperations<String, String> ops = redisTemplate.opsForValue();
            Boolean acquired = ops.setIfAbsent(key, PROCESSING_STATE, PROCESSING_TTL);
            return !Boolean.FALSE.equals(acquired);
        } catch (RuntimeException ex) {
            log.warn("Redis order.created dedup is unavailable, processing without deduplication", ex);
            return true;
        }
    }

    private void markProcessed(String key, String state) {
        try {
            redisTemplate.opsForValue().set(key, state, PROCESSED_TTL);
        } catch (RuntimeException ex) {
            log.warn("Failed to persist order.created dedup state for key={}", key, ex);
        }
    }

    private void clearMarker(String key, boolean markerAcquired) {
        if (!markerAcquired) {
            return;
        }

        try {
            redisTemplate.delete(key);
        } catch (RuntimeException ex) {
            log.warn("Failed to clear order.created dedup state for key={}", key, ex);
        }
    }
}
