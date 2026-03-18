package com.procurehub.order.api.dto;

public class ReserveOrderResponse {
    private boolean success;
    private String message;
    private Long productId;
    private Integer availableQuantity;
    private Integer reservedQuantity;

    public ReserveOrderResponse() {}

    public ReserveOrderResponse(boolean success, String message, Long productId, Integer availableQuantity, Integer reservedQuantity) {
        this.success = success;
        this.message = message;
        this.productId = productId;
        this.availableQuantity = availableQuantity;
        this.reservedQuantity = reservedQuantity;
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public Long getProductId() { return productId; }
    public Integer getAvailableQuantity() { return availableQuantity; }
    public Integer getReservedQuantity() { return reservedQuantity; }

    public void setSuccess(boolean success) { this.success = success; }
    public void setMessage(String message) { this.message = message; }
    public void setProductId(Long productId) { this.productId = productId; }
    public void setAvailableQuantity(Integer availableQuantity) { this.availableQuantity = availableQuantity; }
    public void setReservedQuantity(Integer reservedQuantity) { this.reservedQuantity = reservedQuantity; }
}
