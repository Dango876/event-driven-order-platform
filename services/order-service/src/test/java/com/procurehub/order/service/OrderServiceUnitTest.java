package com.procurehub.order.service;

import com.procurehub.grpc.inventory.v1.CheckAndReserveResponse;
import com.procurehub.order.api.dto.CreateOrderRequest;
import com.procurehub.order.api.dto.OrderResponse;
import com.procurehub.order.client.InventoryGrpcClient;
import com.procurehub.order.event.OrderEventPublisher;
import com.procurehub.order.model.Order;
import com.procurehub.order.model.OrderStatus;
import com.procurehub.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceUnitTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private InventoryGrpcClient inventoryGrpcClient;

    @Mock
    private OrderEventPublisher orderEventPublisher;

    @InjectMocks
    private OrderService orderService;

    private final Map<Long, Order> persistedOrders = new HashMap<>();

    private void setupSaveBehaviorOnly() {
        persistedOrders.clear();

        AtomicLong idSequence = new AtomicLong(1);

        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);

            if (order.getId() == null) {
                ReflectionTestUtils.setField(order, "id", idSequence.getAndIncrement());
            }
            if (order.getCreatedAt() == null) {
                ReflectionTestUtils.setField(order, "createdAt", LocalDateTime.now());
            }
            ReflectionTestUtils.setField(order, "updatedAt", LocalDateTime.now());

            persistedOrders.put(order.getId(), order);
            return order;
        });
    }

    private void setupSaveBehaviorWithLookup() {
        setupSaveBehaviorOnly();

        when(orderRepository.findById(anyLong()))
                .thenAnswer(invocation -> Optional.ofNullable(persistedOrders.get(invocation.getArgument(0))));
    }

    @Test
    void createOrderShouldStayPendingUntilInventoryEventArrives() {
        setupSaveBehaviorOnly();

        CreateOrderRequest request = new CreateOrderRequest();
        request.setUserId(101L);
        request.setProductId(2001L);
        request.setQuantity(2);

        when(inventoryGrpcClient.checkAndReserve(anyLong(), anyLong(), anyInt()))
                .thenReturn(CheckAndReserveResponse.newBuilder()
                        .setSuccess(true)
                        .setMessage("reserved")
                        .setProductId(2001L)
                        .setReservedQuantity(2)
                        .setOrderId(1L)
                        .build());

        OrderResponse response = orderService.createOrder(request);

        assertEquals("RESERVATION_PENDING", response.getStatus());
        assertEquals("Reservation requested", response.getStatusMessage());
        assertEquals(OrderStatus.RESERVATION_PENDING, persistedOrders.get(1L).getStatus());

        verify(orderEventPublisher).publishOrderCreated(any(Order.class));
        verify(orderEventPublisher, never()).publishOrderStatusChanged(any(Order.class), any(OrderStatus.class));
        verify(inventoryGrpcClient).checkAndReserve(1L, 2001L, 2);
    }

    @Test
    void createOrderShouldStayPendingWhenReserveFailsUntilFailureEventArrives() {
        setupSaveBehaviorOnly();

        CreateOrderRequest request = new CreateOrderRequest();
        request.setUserId(101L);
        request.setProductId(2001L);
        request.setQuantity(1);

        when(inventoryGrpcClient.checkAndReserve(anyLong(), anyLong(), anyInt()))
                .thenReturn(CheckAndReserveResponse.newBuilder()
                        .setSuccess(false)
                        .setMessage("not enough stock")
                        .setProductId(2001L)
                        .setOrderId(1L)
                        .build());

        OrderResponse response = orderService.createOrder(request);

        assertEquals("RESERVATION_PENDING", response.getStatus());
        assertEquals("Reservation requested", response.getStatusMessage());
        assertEquals(OrderStatus.RESERVATION_PENDING, persistedOrders.get(1L).getStatus());

        verify(orderEventPublisher).publishOrderCreated(any(Order.class));
        verify(orderEventPublisher, never()).publishOrderStatusChanged(any(Order.class), any(OrderStatus.class));
    }

    @Test
    void handleInventoryReservedShouldTransitionPendingOrderToReserved() {
        setupSaveBehaviorWithLookup();

        Order order = buildOrder(7L, OrderStatus.RESERVATION_PENDING);
        persistedOrders.put(7L, order);

        OrderResponse responseBefore = orderService.getOrder(7L);
        assertEquals("RESERVATION_PENDING", responseBefore.getStatus());

        orderService.handleInventoryReserved(7L, "reserved");

        assertEquals(OrderStatus.RESERVED, order.getStatus());
        assertEquals("Inventory reserved", order.getStatusMessage());
        verify(orderEventPublisher).publishOrderStatusChanged(order, OrderStatus.RESERVATION_PENDING);
    }

    @Test
    void handleInventoryReservationFailedShouldTransitionPendingOrderToFailed() {
        setupSaveBehaviorWithLookup();

        Order order = buildOrder(8L, OrderStatus.RESERVATION_PENDING);
        persistedOrders.put(8L, order);

        orderService.handleInventoryReservationFailed(8L, "not enough stock");

        assertEquals(OrderStatus.RESERVATION_FAILED, order.getStatus());
        assertEquals("Reservation failed: not enough stock", order.getStatusMessage());
        verify(orderEventPublisher).publishOrderStatusChanged(order, OrderStatus.RESERVATION_PENDING);
    }

    @Test
    void handleInventoryReservedShouldCompensateWhenOrderWasCancelled() {
        setupSaveBehaviorWithLookup();

        Order order = buildOrder(9L, OrderStatus.CANCELLED);
        persistedOrders.put(9L, order);

        orderService.handleInventoryReserved(9L, "reserved");

        verify(inventoryGrpcClient).releaseReservation(9L, 2001L, 1);
        verify(orderEventPublisher, never()).publishOrderStatusChanged(any(Order.class), any(OrderStatus.class));
        assertEquals("Cancelled before reservation confirmation; inventory released", order.getStatusMessage());
    }

    @Test
    void getOrderShouldThrowWhenNotFound() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> orderService.getOrder(99L));
        assertTrue(ex.getMessage().contains("Order not found"));
    }

    @Test
    void getAllOrdersShouldMapRepositoryEntities() {
        Order first = buildOrder(1L, OrderStatus.NEW);
        Order second = buildOrder(2L, OrderStatus.PAID);

        when(orderRepository.findAll(any(Sort.class))).thenReturn(List.of(first, second));

        List<OrderResponse> responses = orderService.getAllOrders();

        assertEquals(2, responses.size());
        assertEquals(1L, responses.get(0).getId());
        assertEquals("PAID", responses.get(1).getStatus());
    }

    @Test
    void changeStatusShouldReturnSameOrderWhenStatusUnchanged() {
        Order order = buildOrder(7L, OrderStatus.PAID);
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.changeStatus(7L, "PAID");

        assertEquals("PAID", response.getStatus());
        verify(orderRepository, never()).save(any(Order.class));
        verify(orderEventPublisher, never()).publishOrderStatusChanged(any(Order.class), any(OrderStatus.class));
    }

    @Test
    void changeStatusShouldReleaseReservationWhenReservedToCancelled() {
        setupSaveBehaviorWithLookup();

        Order order = buildOrder(7L, OrderStatus.RESERVED);
        persistedOrders.put(7L, order);

        OrderResponse response = orderService.changeStatus(7L, "cancelled");

        assertEquals("CANCELLED", response.getStatus());
        verify(inventoryGrpcClient).releaseReservation(7L, 2001L, 1);
        verify(orderEventPublisher).publishOrderStatusChanged(order, OrderStatus.RESERVED);
    }

    @Test
    void changeStatusShouldThrowForUnsupportedStatus() {
        Order order = buildOrder(7L, OrderStatus.RESERVATION_PENDING);
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> orderService.changeStatus(7L, "WRONG_STATUS"));

        assertTrue(ex.getMessage().contains("Unsupported status"));
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void changeStatusShouldThrowForInvalidTransition() {
        Order order = buildOrder(7L, OrderStatus.RESERVATION_PENDING);
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> orderService.changeStatus(7L, "PAID"));

        assertTrue(ex.getMessage().contains("Invalid status transition"));
        verify(orderRepository, never()).save(any(Order.class));
    }

    private Order buildOrder(long id, OrderStatus status) {
        Order order = new Order();
        order.setUserId(101L);
        order.setProductId(2001L);
        order.setQuantity(1);
        order.setStatus(status);
        order.setStatusMessage("status: " + status);

        ReflectionTestUtils.setField(order, "id", id);
        ReflectionTestUtils.setField(order, "createdAt", LocalDateTime.now());
        ReflectionTestUtils.setField(order, "updatedAt", LocalDateTime.now());

        return order;
    }
}
