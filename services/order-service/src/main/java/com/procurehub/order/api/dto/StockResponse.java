package com.procurehub.order.api.dto;

public class StockResponse {
    private Long productId;
    private Integer availableQuantity;
    private Integer reservedQuantity;
    private Integer freeQuantity;

    public StockResponse() {}

    public StockResponse(Long productId, Integer availableQuantity, Integer reservedQuantity, Integer freeQuantity) {
        this.productId = productId;
        this.availableQuantity = availableQuantity;
        this.reservedQuantity = reservedQuantity;
        this.freeQuantity = freeQuantity;
    }

    public Long getProductId() { return productId; }
    public Integer getAvailableQuantity() { return availableQuantity; }
    public Integer getReservedQuantity() { return reservedQuantity; }
    public Integer getFreeQuantity() { return freeQuantity; }

    public void setProductId(Long productId) { this.productId = productId; }
    public void setAvailableQuantity(Integer availableQuantity) { this.availableQuantity = availableQuantity; }
    public void setReservedQuantity(Integer reservedQuantity) { this.reservedQuantity = reservedQuantity; }
    public void setFreeQuantity(Integer freeQuantity) { this.freeQuantity = freeQuantity; }
}
