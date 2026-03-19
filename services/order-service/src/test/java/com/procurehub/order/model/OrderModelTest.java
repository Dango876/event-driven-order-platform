package com.procurehub.order.model;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderModelTest {

    @Test
    void onCreateShouldSetDefaultStatusAndTimestamps() {
        Order order = new Order();
        order.setUserId(1L);
        order.setProductId(2L);
        order.setQuantity(3);

        order.onCreate();

        assertEquals(OrderStatus.NEW, order.getStatus());
        assertNotNull(order.getCreatedAt());
        assertNotNull(order.getUpdatedAt());
    }

    @Test
    void onCreateShouldKeepExistingStatus() {
        Order order = new Order();
        order.setStatus(OrderStatus.PAID);

        order.onCreate();

        assertEquals(OrderStatus.PAID, order.getStatus());
    }

    @Test
    void onUpdateShouldChangeUpdatedAt() {
        Order order = new Order();
        order.onCreate();

        LocalDateTime oldUpdated = order.getUpdatedAt();
        ReflectionTestUtils.setField(order, "updatedAt", oldUpdated.minusSeconds(5));
        order.onUpdate();

        assertTrue(order.getUpdatedAt().isAfter(oldUpdated.minusSeconds(5)));
    }

    @Test
    void gettersAndSettersShouldWork() {
        Order order = new Order();
        LocalDateTime created = LocalDateTime.now().minusMinutes(1);
        LocalDateTime updated = LocalDateTime.now();

        order.setUserId(11L);
        order.setProductId(22L);
        order.setQuantity(5);
        order.setStatus(OrderStatus.SHIPPED);
        order.setStatusMessage("shipped");
        ReflectionTestUtils.setField(order, "id", 77L);
        ReflectionTestUtils.setField(order, "createdAt", created);
        ReflectionTestUtils.setField(order, "updatedAt", updated);

        assertEquals(77L, order.getId());
        assertEquals(11L, order.getUserId());
        assertEquals(22L, order.getProductId());
        assertEquals(5, order.getQuantity());
        assertEquals(OrderStatus.SHIPPED, order.getStatus());
        assertEquals("shipped", order.getStatusMessage());
        assertEquals(created, order.getCreatedAt());
        assertEquals(updated, order.getUpdatedAt());
    }
}

