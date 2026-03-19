package com.procurehub.inventory.api;

import com.procurehub.inventory.api.dto.CreateInventoryItemRequest;
import com.procurehub.inventory.api.dto.InventoryItemResponse;
import com.procurehub.inventory.api.dto.ReserveRequest;
import com.procurehub.inventory.service.InventoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/inventory")
@Validated
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping("/items")
    public InventoryItemResponse createItem(@Valid @RequestBody CreateInventoryItemRequest request) {
        return inventoryService.createItem(request);
    }

    @GetMapping("/items/{productId}")
    public InventoryItemResponse getItem(@PathVariable("productId") Long productId) {
        return inventoryService.getByProductId(productId);
    }

    @PostMapping("/reserve")
    public InventoryItemResponse reserve(@Valid @RequestBody ReserveRequest request) {
        return inventoryService.reserve(request.getProductId(), request.getQuantity());
    }

    @PostMapping("/release")
    public InventoryItemResponse release(@Valid @RequestBody ReserveRequest request) {
        return inventoryService.release(request.getProductId(), request.getQuantity());
    }

    @PatchMapping("/items/{productId}/available")
    public InventoryItemResponse updateAvailable(
            @PathVariable("productId") Long productId,
            @RequestParam("quantity") @Min(0) Integer quantity) {
        return inventoryService.updateAvailable(productId, quantity);
    }
}
