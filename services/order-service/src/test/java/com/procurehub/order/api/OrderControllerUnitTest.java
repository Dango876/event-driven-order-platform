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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
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
    void createOrderShouldDelegateToServiceWithAuthenticatedSubject() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setUserId(1L);
        request.setProductId(2L);
        request.setQuantity(3);

        Authentication authentication = authentication("test-user@example.com", "ROLE_USER");
        OrderResponse expected = sampleOrderResponse(10L, "RESERVATION_PENDING");

        when(orderService.createOrder(request, "test-user@example.com")).thenReturn(expected);

        OrderResponse actual = controller.createOrder(request, authentication);

        assertSame(expected, actual);
        verify(orderService).createOrder(request, "test-user@example.com");
    }

    @Test
    void createOrderShouldPreferJwtUserIdWhenAvailable() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setUserId(1L);
        request.setProductId(2L);
        request.setQuantity(3);

        Authentication authentication = jwtAuthentication("test-user@example.com", 55L, "ROLE_USER");
        OrderResponse expected = sampleOrderResponse(11L, "RESERVATION_PENDING");

        when(orderService.createOrder(request, 55L, "test-user@example.com")).thenReturn(expected);

        OrderResponse actual = controller.createOrder(request, authentication);

        assertSame(expected, actual);
        verify(orderService).createOrder(request, 55L, "test-user@example.com");
    }

    @Test
    void getOrderShouldUseAdminPathForAdmin() {
        Authentication authentication = authentication("test-admin@example.com", "ROLE_ADMIN");
        OrderResponse expected = sampleOrderResponse(10L, "RESERVED");

        when(orderService.getOrder(10L)).thenReturn(expected);

        OrderResponse actual = controller.getOrder(10L, authentication);

        assertSame(expected, actual);
        verify(orderService).getOrder(10L);
    }

    @Test
    void getOrderShouldUseOwnerScopedPathForUser() {
        Authentication authentication = authentication("test-user@example.com", "ROLE_USER");
        OrderResponse expected = sampleOrderResponse(10L, "RESERVED");

        when(orderService.getOrder(10L, "test-user@example.com")).thenReturn(expected);

        OrderResponse actual = controller.getOrder(10L, authentication);

        assertSame(expected, actual);
        verify(orderService).getOrder(10L, "test-user@example.com");
    }

    @Test
    void allOrdersShouldUseAdminPathForAdmin() {
        Authentication authentication = authentication("test-admin@example.com", "ROLE_ADMIN");
        List<OrderResponse> expected = List.of(sampleOrderResponse(1L, "NEW"), sampleOrderResponse(2L, "PAID"));

        when(orderService.getAllOrders()).thenReturn(expected);

        List<OrderResponse> actual = controller.allOrders(authentication);

        assertSame(expected, actual);
        verify(orderService).getAllOrders();
    }

    @Test
    void allOrdersShouldUseOwnerScopedPathForUser() {
        Authentication authentication = authentication("test-user@example.com", "ROLE_USER");
        List<OrderResponse> expected = List.of(sampleOrderResponse(1L, "NEW"));

        when(orderService.getAllOrders("test-user@example.com")).thenReturn(expected);

        List<OrderResponse> actual = controller.allOrders(authentication);

        assertSame(expected, actual);
        verify(orderService).getAllOrders("test-user@example.com");
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

    private Authentication authentication(String subject, String authority) {
        return new UsernamePasswordAuthenticationToken(
                subject,
                "n/a",
                List.of(new SimpleGrantedAuthority(authority))
        );
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

    private Authentication jwtAuthentication(String subject, long userId, String authority) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("roles", List.of(authority))
                .claim("userId", userId)
                .build();

        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority(authority)));
    }
}
