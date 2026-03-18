package com.procurehub.order.service;

import com.procurehub.grpc.inventory.v1.CheckAndReserveResponse;
import com.procurehub.order.api.dto.CreateOrderRequest;
import com.procurehub.order.api.dto.OrderResponse;
import com.procurehub.order.client.InventoryGrpcClient;
import com.procurehub.order.event.OrderEventPublisher;
import com.procurehub.order.model.Order;
import com.procurehub.order.model.OrderStatus;
import com.procurehub.order.repository.OrderRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final InventoryGrpcClient inventoryGrpcClient;
    private final OrderEventPublisher orderEventPublisher;

    public OrderService(
            OrderRepository orderRepository,
            InventoryGrpcClient inventoryGrpcClient,
            OrderEventPublisher orderEventPublisher
    ) {
        this.orderRepository = orderRepository;
        this.inventoryGrpcClient = inventoryGrpcClient;
        this.orderEventPublisher = orderEventPublisher;
    }

    public OrderResponse createOrder(CreateOrderRequest request) {
        Order order = new Order();
        order.setUserId(request.getUserId());
        order.setProductId(request.getProductId());
        order.setQuantity(request.getQuantity());
        order.setStatus(OrderStatus.NEW);
        order.setStatusMessage("Order created");

        order = orderRepository.save(order);
        orderEventPublisher.publishOrderCreated(order);

        CheckAndReserveResponse reserve = inventoryGrpcClient.checkAndReserve(
                order.getId(),
                order.getProductId(),
                order.getQuantity()
        );

        if (reserve.getSuccess()) {
            OrderStatus oldStatus = order.getStatus();
            order.setStatus(OrderStatus.RESERVED);
            order.setStatusMessage("Inventory reserved");
            order = orderRepository.save(order);
            orderEventPublisher.publishOrderStatusChanged(order, oldStatus);
            return toResponse(order);
        }

        order.setStatusMessage("Reserve failed: " + reserve.getMessage());
        order = orderRepository.save(order);

        throw new IllegalStateException("Order " + order.getId() + " remains NEW: " + reserve.getMessage());
    }

    public OrderResponse getOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        return toResponse(order);
    }

    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private OrderResponse toResponse(Order order) {
        OrderResponse r = new OrderResponse();
        r.setId(order.getId());
        r.setUserId(order.getUserId());
        r.setProductId(order.getProductId());
        r.setQuantity(order.getQuantity());
        r.setStatus(order.getStatus().name());
        r.setStatusMessage(order.getStatusMessage());
        r.setCreatedAt(order.getCreatedAt());
        r.setUpdatedAt(order.getUpdatedAt());
        return r;
    }

    public OrderResponse changeStatus(Long orderId, String newStatusRaw) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        OrderStatus oldStatus = order.getStatus();
        OrderStatus newStatus;

        try {
            newStatus = OrderStatus.valueOf(newStatusRaw.trim().toUpperCase());
        } catch (Exception e) {
            throw new IllegalArgumentException("Unsupported status: " + newStatusRaw);
        }

        if (oldStatus == newStatus) {
            return toResponse(order);
        }

        validateTransition(oldStatus, newStatus);

        if (oldStatus == OrderStatus.RESERVED && newStatus == OrderStatus.CANCELLED) {
            inventoryGrpcClient.releaseReservation(order.getId(), order.getProductId(), order.getQuantity());
        }

        order.setStatus(newStatus);
        order.setStatusMessage("Status changed: " + oldStatus + " -> " + newStatus);
        order = orderRepository.save(order);

        orderEventPublisher.publishOrderStatusChanged(order, oldStatus);

        return toResponse(order);
    }

    private void validateTransition(OrderStatus oldStatus, OrderStatus newStatus) {
        boolean allowed = switch (oldStatus) {
            case NEW -> newStatus == OrderStatus.RESERVED || newStatus == OrderStatus.CANCELLED;
            case RESERVED -> newStatus == OrderStatus.PAID || newStatus == OrderStatus.CANCELLED;
            case PAID -> newStatus == OrderStatus.SHIPPED || newStatus == OrderStatus.CANCELLED;
            case SHIPPED -> newStatus == OrderStatus.COMPLETED;
            case COMPLETED, CANCELLED -> false;
        };

        if (!allowed) {
            throw new IllegalStateException("Invalid status transition: " + oldStatus + " -> " + newStatus);
        }
    }
}
