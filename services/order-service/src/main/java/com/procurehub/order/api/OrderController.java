package com.procurehub.order.api;

import com.procurehub.order.api.dto.UpdateOrderStatusRequest;
import com.procurehub.grpc.inventory.v1.CheckAndReserveResponse;
import com.procurehub.grpc.inventory.v1.GetStockResponse;
import com.procurehub.grpc.inventory.v1.ReleaseReservationResponse;
import com.procurehub.order.api.dto.CreateOrderRequest;
import com.procurehub.order.api.dto.OrderResponse;
import com.procurehub.order.api.dto.ReserveOrderRequest;
import com.procurehub.order.api.dto.ReserveOrderResponse;
import com.procurehub.order.api.dto.StockResponse;
import com.procurehub.order.client.InventoryGrpcClient;
import com.procurehub.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

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
    public OrderResponse createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return orderService.createOrder(request);
    }

    @GetMapping("/orders/{orderId}")
    public OrderResponse getOrder(@PathVariable("orderId") Long orderId) {
        return orderService.getOrder(orderId);
    }

    @GetMapping("/orders")
    public List<OrderResponse> allOrders() {
        return orderService.getAllOrders();
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
}
