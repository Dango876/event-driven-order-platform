package com.procurehub.order;

import com.procurehub.inventory.avro.InventoryReservedEvent;
import com.procurehub.order.api.dto.CreateOrderRequest;
import com.procurehub.order.api.dto.OrderResponse;
import com.procurehub.order.api.dto.ReserveOrderRequest;
import com.procurehub.order.api.dto.ReserveOrderResponse;
import com.procurehub.order.api.dto.StockResponse;
import com.procurehub.order.api.dto.UpdateOrderStatusRequest;
import com.procurehub.order.api.error.ApiError;
import com.procurehub.order.config.GrpcClientConfig;
import com.procurehub.order.kafka.InventoryEventsListener;
import io.grpc.ManagedChannel;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OrderModuleSupportCoverageTest {

    @Test
    void dtoGettersSettersAndConstructorsShouldWork() {
        CreateOrderRequest createOrderRequest = new CreateOrderRequest();
        createOrderRequest.setUserId(101L);
        createOrderRequest.setProductId(202L);
        createOrderRequest.setQuantity(3);
        assertEquals(101L, createOrderRequest.getUserId());
        assertEquals(202L, createOrderRequest.getProductId());
        assertEquals(3, createOrderRequest.getQuantity());

        ReserveOrderRequest reserveOrderRequest = new ReserveOrderRequest();
        reserveOrderRequest.setProductId(77L);
        reserveOrderRequest.setQuantity(2);
        assertEquals(77L, reserveOrderRequest.getProductId());
        assertEquals(2, reserveOrderRequest.getQuantity());

        ReserveOrderResponse reserveOrderResponse = new ReserveOrderResponse(true, "ok", 77L, 10, 2);
        assertEquals(true, reserveOrderResponse.isSuccess());
        assertEquals("ok", reserveOrderResponse.getMessage());
        assertEquals(10, reserveOrderResponse.getAvailableQuantity());
        reserveOrderResponse.setReservedQuantity(5);
        assertEquals(5, reserveOrderResponse.getReservedQuantity());

        StockResponse stockResponse = new StockResponse(77L, 10, 2, 8);
        assertEquals(8, stockResponse.getFreeQuantity());
        stockResponse.setFreeQuantity(7);
        assertEquals(7, stockResponse.getFreeQuantity());

        UpdateOrderStatusRequest updateOrderStatusRequest = new UpdateOrderStatusRequest();
        updateOrderStatusRequest.setStatus("PAID");
        assertEquals("PAID", updateOrderStatusRequest.getStatus());

        OrderResponse orderResponse = new OrderResponse();
        LocalDateTime now = LocalDateTime.now();
        orderResponse.setId(1L);
        orderResponse.setUserId(2L);
        orderResponse.setProductId(3L);
        orderResponse.setQuantity(4);
        orderResponse.setStatus("RESERVED");
        orderResponse.setStatusMessage("Inventory reserved");
        orderResponse.setCreatedAt(now);
        orderResponse.setUpdatedAt(now);
        assertEquals(1L, orderResponse.getId());
        assertEquals("RESERVED", orderResponse.getStatus());
    }

    @Test
    void apiErrorRecordShouldExposeFields() {
        LocalDateTime now = LocalDateTime.now();
        ApiError error = new ApiError(now, 409, "Conflict");
        assertEquals(now, error.timestamp());
        assertEquals(409, error.status());
        assertEquals("Conflict", error.message());
    }

    @Test
    void grpcConfigShouldCreateChannelAndStub() {
        GrpcClientConfig config = new GrpcClientConfig();
        ManagedChannel channel = config.inventoryManagedChannel("localhost", 65535);
        try {
            assertNotNull(channel);
            assertNotNull(config.inventoryBlockingStub(channel));
        } finally {
            channel.shutdownNow();
        }
    }

    @Test
    void inventoryListenerShouldHandleEventWithoutThrowing() {
        InventoryEventsListener listener = new InventoryEventsListener();
        InventoryReservedEvent event = InventoryReservedEvent.newBuilder()
                .setOrderId(1L)
                .setProductId(2001L)
                .setQuantity(1)
                .setAvailableQuantity(10)
                .setReservedQuantity(1)
                .setMessage("reserved")
                .setReservedAt(LocalDateTime.now().toString())
                .build();

        assertDoesNotThrow(() -> listener.onInventoryReserved(event));
    }
}
