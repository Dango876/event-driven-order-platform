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
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class OrderController {

    private final InventoryGrpcClient inventoryGrpcClient;
    private final OrderService orderService;

    public OrderController(InventoryGrpcClient inventoryGrpcClient, OrderService orderService) {
        this.inventoryGrpcClient = inventoryGrpcClient;
        this.orderService = orderService;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @PostMapping("/orders")
    public OrderResponse createOrder(@Valid @RequestBody CreateOrderRequest request, Authentication authentication) {
        Long authenticatedUserId = extractAuthenticatedUserId(authentication);
        String ownerSubject = authentication.getName();

        if (authenticatedUserId != null) {
            return orderService.createOrder(request, authenticatedUserId, ownerSubject);
        }

        return orderService.createOrder(request, ownerSubject);
    }

    @GetMapping("/orders/{orderId}")
    public OrderResponse getOrder(@PathVariable("orderId") Long orderId, Authentication authentication) {
        if (isAdmin(authentication)) {
            return orderService.getOrder(orderId);
        }

        return orderService.getOrder(orderId, authentication.getName());
    }

    @GetMapping("/orders")
    public List<OrderResponse> allOrders(Authentication authentication) {
        if (isAdmin(authentication)) {
            return orderService.getAllOrders();
        }

        return orderService.getAllOrders(authentication.getName());
    }

    @PostMapping("/orders/reserve-check")
    public ReserveOrderResponse reserveCheck(@Valid @RequestBody ReserveOrderRequest request) {
        CheckAndReserveResponse response = inventoryGrpcClient.checkAndReserve(request.getProductId(), request.getQuantity());

        return new ReserveOrderResponse(
                response.getSuccess(),
                response.getMessage(),
                response.getProductId(),
                response.getAvailableQuantity(),
                response.getReservedQuantity()
        );
    }

    @PostMapping("/orders/release-check")
    public ReserveOrderResponse releaseCheck(@Valid @RequestBody ReserveOrderRequest request) {
        ReleaseReservationResponse response = inventoryGrpcClient.releaseReservation(request.getProductId(), request.getQuantity());

        return new ReserveOrderResponse(
                response.getSuccess(),
                response.getMessage(),
                response.getProductId(),
                response.getAvailableQuantity(),
                response.getReservedQuantity()
        );
    }

    @GetMapping("/orders/stock/{productId}")
    public StockResponse stock(@PathVariable("productId") Long productId) {
        GetStockResponse response = inventoryGrpcClient.getStock(productId);

        return new StockResponse(
                response.getProductId(),
                response.getAvailableQuantity(),
                response.getReservedQuantity(),
                response.getFreeQuantity()
        );
    }

    @PatchMapping("/orders/{orderId}/status")
    public OrderResponse changeStatus(
            @PathVariable("orderId") Long orderId,
            @Valid @RequestBody UpdateOrderStatusRequest request
    ) {
        return orderService.changeStatus(orderId, request.getStatus());
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    private Long extractAuthenticatedUserId(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            Object claim = jwt.getClaims().get("userId");
            if (claim instanceof Number number) {
                return number.longValue();
            }
            if (claim instanceof String value && !value.isBlank()) {
                try {
                    return Long.parseLong(value);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }
}
