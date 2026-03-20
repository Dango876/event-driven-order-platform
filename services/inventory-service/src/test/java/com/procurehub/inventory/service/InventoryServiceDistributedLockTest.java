package com.procurehub.inventory.service;

import com.procurehub.inventory.api.dto.CreateInventoryItemRequest;
import com.procurehub.inventory.api.dto.InventoryItemResponse;
import com.procurehub.inventory.model.InventoryItem;
import com.procurehub.inventory.repository.InventoryItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceDistributedLockTest {

    @Mock
    private InventoryItemRepository repository;

    @Mock
    private RedisDistributedLockService distributedLockService;

    @InjectMocks
    private InventoryService inventoryService;

    @Test
    void reserveShouldAcquireDistributedLockAndUpdateReservedQuantity() {
        mockLockExecution();
        long productId = 1001L;
        int quantityToReserve = 3;

        InventoryItem item = new InventoryItem();
        item.setProductId(productId);
        item.setAvailableQuantity(10);
        item.setReservedQuantity(2);

        when(repository.findByProductIdForUpdate(productId)).thenReturn(Optional.of(item));
        when(repository.save(any(InventoryItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InventoryItemResponse response = inventoryService.reserve(productId, quantityToReserve);

        assertEquals(5, response.getReservedQuantity());
        assertEquals(5, response.getFreeQuantity());
        verify(distributedLockService).withProductLock(eq(productId), any());
    }

    @Test
    void createItemShouldAcquireDistributedLock() {
        mockLockExecution();
        long productId = 2001L;
        CreateInventoryItemRequest request = new CreateInventoryItemRequest();
        request.setProductId(productId);
        request.setAvailableQuantity(15);

        when(repository.findByProductId(productId)).thenReturn(Optional.empty());
        when(repository.save(any(InventoryItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InventoryItemResponse response = inventoryService.createItem(request);

        assertEquals(productId, response.getProductId());
        assertEquals(15, response.getAvailableQuantity());
        assertEquals(0, response.getReservedQuantity());
        verify(distributedLockService).withProductLock(eq(productId), any());
    }

    @Test
    void reserveShouldPropagateDistributedLockException() {
        long productId = 3001L;
        doThrow(new DistributedLockException("lock unavailable"))
                .when(distributedLockService)
                .withProductLock(eq(productId), any());

        assertThrows(DistributedLockException.class, () -> inventoryService.reserve(productId, 1));
        verifyNoInteractions(repository);
    }

    private void mockLockExecution() {
        when(distributedLockService.withProductLock(anyLong(), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> action = invocation.getArgument(1);
                    return action.get();
                });
    }
}
