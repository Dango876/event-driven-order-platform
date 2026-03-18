package com.procurehub.order.api.dto;

import java.time.LocalDateTime;

public class OrderResponse {
    private Long id;
    private Long userId;
    private Long productId;
    private Integer quantity;
    private String status;
    private String statusMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getProductId() { return productId; }
    public Integer getQuantity() { return quantity; }
    public String getStatus() { return status; }
    public String getStatusMessage() { return statusMessage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setId(Long id) { this.id = id; }
    public void setUserId(Long userId) { this.userId = userId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public void setStatus(String status) { this.status = status; }
    public void setStatusMessage(String statusMessage) { this.statusMessage = statusMessage; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
