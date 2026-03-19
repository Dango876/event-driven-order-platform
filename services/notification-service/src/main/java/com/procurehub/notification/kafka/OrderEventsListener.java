package com.procurehub.notification.kafka;

import com.procurehub.order.avro.OrderCreatedEvent;
import com.procurehub.order.avro.OrderStatusChangedEvent;
import com.procurehub.notification.ratelimit.NotificationRateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventsListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventsListener.class);
    private final NotificationRateLimiter rateLimiter;

    public OrderEventsListener(NotificationRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @KafkaListener(topics = "${app.kafka.topics.order-created}", groupId = "${spring.kafka.consumer.group-id}")
    public void onOrderCreated(OrderCreatedEvent event) {
        long orderId = event.getOrderId();
        long userId = event.getUserId();

        rateLimiter.rememberOrderUser(orderId, userId);
        if (!rateLimiter.allowForUser(userId)) {
            log.warn("Notification rate limit exceeded for userId={}, skip order.created orderId={}", userId, orderId);
            return;
        }

        log.info("Notification: order created, orderId={}, userId={}, status={}",
                event.getOrderId(), event.getUserId(), event.getStatus());
    }

    @KafkaListener(topics = "${app.kafka.topics.order-status-changed}", groupId = "${spring.kafka.consumer.group-id}")
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        long orderId = event.getOrderId();

        Long userId = rateLimiter.findUserIdByOrder(orderId);
        if (userId != null) {
            if (!rateLimiter.allowForUser(userId)) {
                log.warn(
                        "Notification rate limit exceeded for userId={}, skip order.status-changed orderId={}",
                        userId,
                        orderId
                );
                return;
            }
        } else if (!rateLimiter.allowForOrderFallback(orderId)) {
            log.warn(
                    "Notification rate limit exceeded for fallback order bucket, skip order.status-changed orderId={}",
                    orderId
            );
            return;
        }

        log.info("Notification: order status changed, orderId={}, {} -> {}, message={}",
                event.getOrderId(), event.getOldStatus(), event.getNewStatus(), event.getStatusMessage());
    }
}
