package com.procurehub.notification.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "notification_records")
public class NotificationRecord {

    @Id
    private String id;

    private NotificationType type;
    private NotificationStatus status;

    private Long userId;
    private Long orderId;
    private String recipientEmail;

    private String subject;
    private String body;

    private String sourceEventId;
    private String failureReason;

    private Instant createdAt;
    private Instant sentAt;

    public NotificationRecord() {
    }

    public NotificationRecord(String id,
                              NotificationType type,
                              NotificationStatus status,
                              Long userId,
                              Long orderId,
                              String recipientEmail,
                              String subject,
                              String body,
                              String sourceEventId,
                              String failureReason,
                              Instant createdAt,
                              Instant sentAt) {
        this.id = id;
        this.type = type;
        this.status = status;
        this.userId = userId;
        this.orderId = orderId;
        this.recipientEmail = recipientEmail;
        this.subject = subject;
        this.body = body;
        this.sourceEventId = sourceEventId;
        this.failureReason = failureReason;
        this.createdAt = createdAt;
        this.sentAt = sentAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public NotificationType getType() {
        return type;
    }

    public void setType(NotificationType type) {
        this.type = type;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public void setStatus(NotificationStatus status) {
        this.status = status;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getRecipientEmail() {
        return recipientEmail;
    }

    public void setRecipientEmail(String recipientEmail) {
        this.recipientEmail = recipientEmail;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getSourceEventId() {
        return sourceEventId;
    }

    public void setSourceEventId(String sourceEventId) {
        this.sourceEventId = sourceEventId;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public void setSentAt(Instant sentAt) {
        this.sentAt = sentAt;
    }
}
