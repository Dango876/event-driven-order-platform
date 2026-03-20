package com.procurehub.inventory.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class CreateInventoryItemRequest {

    @NotNull
    private Long productId;

    @NotNull
    @Min(0)
    private Integer availableQuantity;

    public Long getProductId() { return productId; }
    public Integer getAvailableQuantity() { return availableQuantity; }

    public void setProductId(Long productId) { this.productId = productId; }
    public void setAvailableQuantity(Integer availableQuantity) { this.availableQuantity = availableQuantity; }
}
