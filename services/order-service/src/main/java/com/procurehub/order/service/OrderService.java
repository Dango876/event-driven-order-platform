package com.procurehub.order.service;

import com.procurehub.grpc.inventory.v1.CheckAndReserveResponse;
import com.procurehub.order.api.dto.CreateOrderRequest;
import com.procurehub.order.api.dto.OrderResponse;
import com.procurehub.order.client.InventoryGrpcClient;
import com.procurehub.order.event.OrderEventPublisher;
import com.procurehub.order.model.Order;
import com.procurehub.order.model.OrderStatus;
import com.procurehub.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

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
        order.setStatus(OrderStatus.RESERVATION_PENDING);
        order.setStatusMessage("Reservation requested");

        order = orderRepository.save(order);
        orderEventPublisher.publishOrderCreated(order);

        CheckAndReserveResponse reserve = inventoryGrpcClient.checkAndReserve(
                order.getId(),
                order.getProductId(),
                order.getQuantity()
        );

        if (!reserve.getSuccess()) {
            log.info(
                    "Inventory reservation rejected for orderId={}, waiting for inventory.reserve-failed event",
                    order.getId()
            );
        }
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long orderId) {
        return toResponse(findRequiredOrder(orderId));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public OrderResponse changeStatus(Long orderId, String newStatusRaw) {
        Order order = findRequiredOrder(orderId);

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

    @Transactional
    public void handleInventoryReserved(long orderId, String message) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("Received inventory.reserved for missing orderId={}", orderId);
            return;
        }

        if (order.getStatus() == OrderStatus.RESERVED) {
            log.debug("Ignore duplicate inventory.reserved for orderId={}", orderId);
            return;
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            inventoryGrpcClient.releaseReservation(order.getId(), order.getProductId(), order.getQuantity());
            order.setStatusMessage("Cancelled before reservation confirmation; inventory released");
            orderRepository.save(order);
            log.info("Compensated late inventory.reserved for cancelled orderId={}", orderId);
            return;
        }

        if (!isReservationPendingState(order.getStatus())) {
            log.warn(
                    "Ignore inventory.reserved for orderId={} in status={}",
                    orderId,
                    order.getStatus()
            );
            return;
        }

        OrderStatus oldStatus = order.getStatus();
        order.setStatus(OrderStatus.RESERVED);
        order.setStatusMessage("Inventory reserved");
        order = orderRepository.save(order);

        orderEventPublisher.publishOrderStatusChanged(order, oldStatus);
    }

    @Transactional
    public void handleInventoryReservationFailed(long orderId, String message) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("Received inventory.reserve-failed for missing orderId={}", orderId);
            return;
        }

        if (order.getStatus() == OrderStatus.RESERVATION_FAILED) {
            log.debug("Ignore duplicate inventory.reserve-failed for orderId={}", orderId);
            return;
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            log.info("Ignore inventory.reserve-failed for already cancelled orderId={}", orderId);
            return;
        }

        if (!isReservationPendingState(order.getStatus())) {
            log.warn(
                    "Ignore inventory.reserve-failed for orderId={} in status={}",
                    orderId,
                    order.getStatus()
            );
            return;
        }

        OrderStatus oldStatus = order.getStatus();
        order.setStatus(OrderStatus.RESERVATION_FAILED);
        order.setStatusMessage("Reservation failed: " + normalizeFailureMessage(message));
        order = orderRepository.save(order);

        orderEventPublisher.publishOrderStatusChanged(order, oldStatus);
    }

    private boolean isReservationPendingState(OrderStatus status) {
        return status == OrderStatus.NEW || status == OrderStatus.RESERVATION_PENDING;
    }

    private String normalizeFailureMessage(String message) {
        if (message == null || message.isBlank()) {
            return "inventory reservation was rejected";
        }
        return message;
    }

    private Order findRequiredOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
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

    private void validateTransition(OrderStatus oldStatus, OrderStatus newStatus) {
        boolean allowed = switch (oldStatus) {
            case NEW -> newStatus == OrderStatus.RESERVATION_PENDING || newStatus == OrderStatus.CANCELLED;
            case RESERVATION_PENDING -> newStatus == OrderStatus.RESERVED
                    || newStatus == OrderStatus.RESERVATION_FAILED
                    || newStatus == OrderStatus.CANCELLED;
            case RESERVATION_FAILED -> newStatus == OrderStatus.CANCELLED;
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
