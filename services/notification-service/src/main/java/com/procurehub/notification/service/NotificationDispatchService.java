package com.procurehub.notification.service;

import com.procurehub.notification.model.NotificationRecord;
import com.procurehub.notification.model.NotificationStatus;
import com.procurehub.notification.model.NotificationType;
import com.procurehub.notification.model.NotificationUser;
import com.procurehub.notification.ratelimit.NotificationRateLimiter;
import com.procurehub.notification.repository.NotificationRecordRepository;
import com.procurehub.notification.repository.NotificationUserRepository;
import com.procurehub.notification.sender.EmailNotificationSender;
import com.procurehub.order.avro.OrderCreatedEvent;
import com.procurehub.order.avro.OrderStatusChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Optional;

@Service
public class NotificationDispatchService {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatchService.class);

    private final NotificationRateLimiter rateLimiter;
    private final NotificationUserRepository notificationUserRepository;
    private final NotificationRecordRepository notificationRecordRepository;
    private final EmailNotificationSender emailNotificationSender;

    public NotificationDispatchService(NotificationRateLimiter rateLimiter,
                                       NotificationUserRepository notificationUserRepository,
                                       NotificationRecordRepository notificationRecordRepository,
                                       EmailNotificationSender emailNotificationSender) {
        this.rateLimiter = rateLimiter;
        this.notificationUserRepository = notificationUserRepository;
        this.notificationRecordRepository = notificationRecordRepository;
        this.emailNotificationSender = emailNotificationSender;
    }

    public void handleOrderCreated(OrderCreatedEvent event) {
        long orderId = event.getOrderId();
        long userId = event.getUserId();

        rateLimiter.rememberOrderUser(orderId, userId);

        RecipientResolution recipient = resolveRecipientForUser(userId);

        dispatch(
                NotificationType.ORDER_CREATED,
                "order-created:" + orderId,
                orderId,
                userId,
                recipient.recipientEmail(),
                "Order #" + orderId + " created",
                buildOrderCreatedBody(event)
        );
    }

    public void handleOrderStatusChanged(OrderStatusChangedEvent event) {
        long orderId = event.getOrderId();

        RecipientResolution recipient = resolveRecipientForOrder(orderId);

        dispatch(
                NotificationType.ORDER_STATUS_CHANGED,
                "order-status-changed:" + orderId + ":" + event.getNewStatus() + ":" + safeValue(event.getUpdatedAt()),
                orderId,
                recipient.userId(),
                recipient.recipientEmail(),
                "Order #" + orderId + " status changed to " + event.getNewStatus(),
                buildOrderStatusChangedBody(event)
        );
    }

    private void dispatch(NotificationType type,
                          String sourceEventId,
                          long orderId,
                          Long userId,
                          String recipientEmail,
                          String subject,
                          String body) {
        Instant now = Instant.now();

        if (!StringUtils.hasText(recipientEmail)) {
            NotificationRecord skipped = buildRecord(
                    type,
                    NotificationStatus.SKIPPED_NO_RECIPIENT,
                    userId,
                    orderId,
                    null,
                    subject,
                    body,
                    sourceEventId,
                    "Recipient email not found",
                    now,
                    null
            );
            notificationRecordRepository.save(skipped);

            log.warn("Skip notification: no recipient found for orderId={}, type={}", orderId, type);
            return;
        }

        boolean allowed = userId != null
                ? rateLimiter.allowForUser(userId)
                : rateLimiter.allowForOrderFallback(orderId);

        if (!allowed) {
            NotificationRecord skipped = buildRecord(
                    type,
                    NotificationStatus.SKIPPED_RATE_LIMIT,
                    userId,
                    orderId,
                    recipientEmail,
                    subject,
                    body,
                    sourceEventId,
                    "Rate limit exceeded",
                    now,
                    null
            );
            notificationRecordRepository.save(skipped);

            log.warn("Skip notification due to rate limit for orderId={}, userId={}, type={}", orderId, userId, type);
            return;
        }

        NotificationRecord record = buildRecord(
                type,
                NotificationStatus.PENDING,
                userId,
                orderId,
                recipientEmail,
                subject,
                body,
                sourceEventId,
                null,
                now,
                null
        );

        record = notificationRecordRepository.save(record);

        try {
            emailNotificationSender.send(recipientEmail, subject, body);
            record.setStatus(NotificationStatus.SENT);
            record.setSentAt(Instant.now());
            record.setFailureReason(null);

            log.info("Notification sent successfully for orderId={}, type={}, recipient={}", orderId, type, recipientEmail);
        } catch (Exception ex) {
            record.setStatus(NotificationStatus.FAILED);
            record.setFailureReason(ex.getMessage());

            log.error(
                    "Failed to send notification for orderId={}, type={}, recipient={}",
                    orderId,
                    type,
                    recipientEmail,
                    ex
            );
        }

        notificationRecordRepository.save(record);
    }

    private RecipientResolution resolveRecipientForUser(long userId) {
        return notificationUserRepository.findById(userId)
                .map(user -> new RecipientResolution(userId, user.getEmail()))
                .orElse(new RecipientResolution(userId, null));
    }

    private RecipientResolution resolveRecipientForOrder(long orderId) {
        Long cachedUserId = rateLimiter.findUserIdByOrder(orderId);
        Long fallbackUserId = null;

        if (cachedUserId != null) {
            RecipientResolution fromUserProjection = resolveRecipientForUser(cachedUserId);
            if (StringUtils.hasText(fromUserProjection.recipientEmail())) {
                return fromUserProjection;
            }
            fallbackUserId = cachedUserId;
        }

        Optional<NotificationRecord> latestRecord = notificationRecordRepository.findFirstByOrderIdOrderByCreatedAtDesc(orderId);
        if (latestRecord.isPresent()) {
            NotificationRecord record = latestRecord.get();

            if (record.getUserId() != null) {
                rateLimiter.rememberOrderUser(orderId, record.getUserId());
            }

            if (StringUtils.hasText(record.getRecipientEmail())) {
                return new RecipientResolution(record.getUserId(), record.getRecipientEmail());
            }

            if (record.getUserId() != null) {
                return resolveRecipientForUser(record.getUserId());
            }
        }

        if (fallbackUserId != null) {
            return new RecipientResolution(fallbackUserId, null);
        }

        return new RecipientResolution(null, null);
    }

    private NotificationRecord buildRecord(NotificationType type,
                                           NotificationStatus status,
                                           Long userId,
                                           long orderId,
                                           String recipientEmail,
                                           String subject,
                                           String body,
                                           String sourceEventId,
                                           String failureReason,
                                           Instant createdAt,
                                           Instant sentAt) {
        return new NotificationRecord(
                null,
                type,
                status,
                userId,
                orderId,
                recipientEmail,
                subject,
                body,
                sourceEventId,
                failureReason,
                createdAt,
                sentAt
        );
    }

    private String buildOrderCreatedBody(OrderCreatedEvent event) {
        return "Hello!\n\n"
                + "Your order has been created.\n"
                + "Order ID: " + event.getOrderId() + "\n"
                + "Product ID: " + event.getProductId() + "\n"
                + "Quantity: " + event.getQuantity() + "\n"
                + "Status: " + event.getStatus() + "\n"
                + "Message: " + safeValue(event.getStatusMessage()) + "\n\n"
                + "ProcureHub Notification Service";
    }

    private String buildOrderStatusChangedBody(OrderStatusChangedEvent event) {
        return "Hello!\n\n"
                + "Your order status has changed.\n"
                + "Order ID: " + event.getOrderId() + "\n"
                + "Old status: " + event.getOldStatus() + "\n"
                + "New status: " + event.getNewStatus() + "\n"
                + "Message: " + safeValue(event.getStatusMessage()) + "\n"
                + "Updated at: " + safeValue(event.getUpdatedAt()) + "\n\n"
                + "ProcureHub Notification Service";
    }

    private String safeValue(String value) {
        return StringUtils.hasText(value) ? value : "n/a";
    }

    private record RecipientResolution(Long userId, String recipientEmail) {
    }
}
