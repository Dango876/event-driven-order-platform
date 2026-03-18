package com.procurehub.inventory.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class ReserveRequest {

    @NotNull
    private Long productId;

    @NotNull
    @Min(1)
    private Integer quantity;

    public Long getProductId() { return productId; }
    public Integer getQuantity() { return quantity; }

    public void setProductId(Long productId) { this.productId = productId; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
}
