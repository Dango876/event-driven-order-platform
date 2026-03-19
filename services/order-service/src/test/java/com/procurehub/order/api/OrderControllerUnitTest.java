package com.procurehub.order.api;

import com.procurehub.grpc.inventory.v1.CheckAndReserveResponse;
import com.procurehub.grpc.inventory.v1.GetStockResponse;
import com.procurehub.grpc.inventory.v1.ReleaseReservationResponse;
import com.procurehub.order.api.dto.CreateOrderRequest;
import com.procurehub.order.api.dto.OrderResponse;
import com.procurehub.order.api.dto.ReserveOrderRequest;
import com.procurehub.order.api.dto.ReserveOrderResponse;
import com.procurehub.order.api.dto.StockResponse;
import com.procurehub.order.api.dto.UpdateOrderStatusRequest;
import com.procurehub.order.client.InventoryGrpcClient;
import com.procurehub.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderControllerUnitTest {

    @Mock
    private InventoryGrpcClient inventoryGrpcClient;

    @Mock
    private OrderService orderService;

    @InjectMocks
    private OrderController controller;

    @Test
    void healthShouldReturnOk() {
        Map<String, String> response = controller.health();
        assertEquals("ok", response.get("status"));
    }

    @Test
    void createOrderShouldDelegateToService() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setUserId(1L);
        request.setProductId(2L);
        request.setQuantity(3);
        OrderResponse expected = sampleOrderResponse(10L, "NEW");

        when(orderService.createOrder(request)).thenReturn(expected);
        OrderResponse actual = controller.createOrder(request);

        assertSame(expected, actual);
        verify(orderService).createOrder(request);
    }

    @Test
    void getOrderShouldDelegateToService() {
        OrderResponse expected = sampleOrderResponse(10L, "RESERVED");
        when(orderService.getOrder(10L)).thenReturn(expected);

        OrderResponse actual = controller.getOrder(10L);

        assertSame(expected, actual);
        verify(orderService).getOrder(10L);
    }

    @Test
    void allOrdersShouldDelegateToService() {
        List<OrderResponse> expected = List.of(sampleOrderResponse(1L, "NEW"), sampleOrderResponse(2L, "PAID"));
        when(orderService.getAllOrders()).thenReturn(expected);

        List<OrderResponse> actual = controller.allOrders();

        assertSame(expected, actual);
        verify(orderService).getAllOrders();
    }

    @Test
    void reserveCheckShouldMapGrpcResponse() {
        ReserveOrderRequest request = new ReserveOrderRequest();
        request.setProductId(1001L);
        request.setQuantity(2);

        CheckAndReserveResponse grpc = CheckAndReserveResponse.newBuilder()
                .setSuccess(true)
                .setMessage("reserved")
                .setProductId(1001L)
                .setAvailableQuantity(20)
                .setReservedQuantity(2)
                .build();

        when(inventoryGrpcClient.checkAndReserve(1001L, 2)).thenReturn(grpc);

        ReserveOrderResponse response = controller.reserveCheck(request);

        assertEquals(true, response.isSuccess());
        assertEquals("reserved", response.getMessage());
        assertEquals(1001L, response.getProductId());
        assertEquals(20, response.getAvailableQuantity());
        assertEquals(2, response.getReservedQuantity());
    }

    @Test
    void releaseCheckShouldMapGrpcResponse() {
        ReserveOrderRequest request = new ReserveOrderRequest();
        request.setProductId(1001L);
        request.setQuantity(1);

        ReleaseReservationResponse grpc = ReleaseReservationResponse.newBuilder()
                .setSuccess(true)
                .setMessage("released")
                .setProductId(1001L)
                .setAvailableQuantity(21)
                .setReservedQuantity(1)
                .build();

        when(inventoryGrpcClient.releaseReservation(1001L, 1)).thenReturn(grpc);

        ReserveOrderResponse response = controller.releaseCheck(request);

        assertEquals(true, response.isSuccess());
        assertEquals("released", response.getMessage());
        assertEquals(21, response.getAvailableQuantity());
    }

    @Test
    void stockShouldMapGrpcResponse() {
        GetStockResponse grpc = GetStockResponse.newBuilder()
                .setProductId(1001L)
                .setAvailableQuantity(21)
                .setReservedQuantity(1)
                .setFreeQuantity(20)
                .build();
        when(inventoryGrpcClient.getStock(1001L)).thenReturn(grpc);

        StockResponse response = controller.stock(1001L);

        assertEquals(1001L, response.getProductId());
        assertEquals(20, response.getFreeQuantity());
    }

    @Test
    void changeStatusShouldDelegateToService() {
        UpdateOrderStatusRequest request = new UpdateOrderStatusRequest();
        request.setStatus("PAID");
        OrderResponse expected = sampleOrderResponse(10L, "PAID");
        when(orderService.changeStatus(10L, "PAID")).thenReturn(expected);

        OrderResponse actual = controller.changeStatus(10L, request);

        assertSame(expected, actual);
        verify(orderService).changeStatus(10L, "PAID");
    }

    private OrderResponse sampleOrderResponse(Long id, String status) {
        OrderResponse response = new OrderResponse();
        response.setId(id);
        response.setUserId(1L);
        response.setProductId(2L);
        response.setQuantity(1);
        response.setStatus(status);
        response.setStatusMessage("status: " + status);
        response.setCreatedAt(LocalDateTime.now());
        response.setUpdatedAt(LocalDateTime.now());
        return response;
    }
}

