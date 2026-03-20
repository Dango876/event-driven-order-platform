package com.procurehub.inventory.service;

import com.procurehub.inventory.api.dto.CreateInventoryItemRequest;
import com.procurehub.inventory.api.dto.InventoryItemResponse;
import com.procurehub.inventory.model.InventoryItem;
import com.procurehub.inventory.repository.InventoryItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

    private final InventoryItemRepository repository;
    private final RedisDistributedLockService distributedLockService;

    public InventoryService(InventoryItemRepository repository,
                            RedisDistributedLockService distributedLockService) {
        this.repository = repository;
        this.distributedLockService = distributedLockService;
    }

    @Transactional
    public InventoryItemResponse createItem(CreateInventoryItemRequest request) {
        return distributedLockService.withProductLock(request.getProductId(), () -> {
            repository.findByProductId(request.getProductId()).ifPresent(i -> {
                throw new IllegalArgumentException("Inventory item already exists for productId=" + request.getProductId());
            });

            InventoryItem item = new InventoryItem();
            item.setProductId(request.getProductId());
            item.setAvailableQuantity(request.getAvailableQuantity());
            item.setReservedQuantity(0);

            return toResponse(repository.save(item));
        });
    }

    @Transactional(readOnly = true)
    public InventoryItemResponse getByProductId(Long productId) {
        InventoryItem item = repository.findByProductId(productId)
                .orElseThrow(() -> new IllegalArgumentException("Inventory item not found for productId=" + productId));
        return toResponse(item);
    }

    @Transactional
    public InventoryItemResponse reserve(Long productId, Integer quantity) {
        return distributedLockService.withProductLock(productId, () -> {
            InventoryItem item = repository.findByProductIdForUpdate(productId)
                    .orElseThrow(() -> new IllegalArgumentException("Inventory item not found for productId=" + productId));

            int free = item.getAvailableQuantity() - item.getReservedQuantity();
            if (free < quantity) {
                throw new NotEnoughStockException("Not enough stock: free=" + free + ", requested=" + quantity);
            }

            item.setReservedQuantity(item.getReservedQuantity() + quantity);
            return toResponse(repository.save(item));
        });
    }

    @Transactional
    public InventoryItemResponse release(Long productId, Integer quantity) {
        return distributedLockService.withProductLock(productId, () -> {
            InventoryItem item = repository.findByProductIdForUpdate(productId)
                    .orElseThrow(() -> new IllegalArgumentException("Inventory item not found for productId=" + productId));

            if (item.getReservedQuantity() < quantity) {
                throw new IllegalArgumentException("Cannot release more than reserved. reserved=" + item.getReservedQuantity());
            }

            item.setReservedQuantity(item.getReservedQuantity() - quantity);
            return toResponse(repository.save(item));
        });
    }

    @Transactional
    public InventoryItemResponse updateAvailable(Long productId, Integer quantity) {
        return distributedLockService.withProductLock(productId, () -> {
            InventoryItem item = repository.findByProductIdForUpdate(productId)
                    .orElseThrow(() -> new IllegalArgumentException("Inventory item not found for productId=" + productId));

            if (quantity < item.getReservedQuantity()) {
                throw new IllegalArgumentException("availableQuantity cannot be less than reservedQuantity");
            }

            item.setAvailableQuantity(quantity);
            return toResponse(repository.save(item));
        });
    }

    private InventoryItemResponse toResponse(InventoryItem item) {
        InventoryItemResponse response = new InventoryItemResponse();
        response.setId(item.getId());
        response.setProductId(item.getProductId());
        response.setAvailableQuantity(item.getAvailableQuantity());
        response.setReservedQuantity(item.getReservedQuantity());
        response.setFreeQuantity(item.getAvailableQuantity() - item.getReservedQuantity());
        response.setCreatedAt(item.getCreatedAt());
        response.setUpdatedAt(item.getUpdatedAt());
        return response;
    }
}
