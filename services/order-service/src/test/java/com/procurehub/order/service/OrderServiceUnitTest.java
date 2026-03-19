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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
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

    private void setupSaveBehavior() {
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
            return order;
        });
    }

    @Test
    void createOrderShouldReserveInventoryWhenGrpcSucceeds() {
        setupSaveBehavior();

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
                        .build());

        OrderResponse response = orderService.createOrder(request);

        assertEquals("RESERVED", response.getStatus());
        assertEquals("Inventory reserved", response.getStatusMessage());
        verify(orderEventPublisher).publishOrderCreated(any(Order.class));
        verify(orderEventPublisher).publishOrderStatusChanged(any(Order.class), any(OrderStatus.class));
        verify(inventoryGrpcClient).checkAndReserve(1L, 2001L, 2);
    }

    @Test
    void createOrderShouldStayNewWhenReserveFails() {
        setupSaveBehavior();

        CreateOrderRequest request = new CreateOrderRequest();
        request.setUserId(101L);
        request.setProductId(2001L);
        request.setQuantity(1);

        when(inventoryGrpcClient.checkAndReserve(anyLong(), anyLong(), anyInt()))
                .thenReturn(CheckAndReserveResponse.newBuilder()
                        .setSuccess(false)
                        .setMessage("not enough stock")
                        .setProductId(2001L)
                        .build());

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> orderService.createOrder(request));
        assertTrue(ex.getMessage().contains("remains NEW"));
        verify(orderEventPublisher).publishOrderCreated(any(Order.class));
        verify(orderEventPublisher, never()).publishOrderStatusChanged(any(Order.class), any(OrderStatus.class));
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

        ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);
        verify(orderRepository).findAll(sortCaptor.capture());
        Sort sort = sortCaptor.getValue();
        assertTrue(sort.getOrderFor("id").isAscending());
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
        setupSaveBehavior();

        Order order = buildOrder(7L, OrderStatus.RESERVED);
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.changeStatus(7L, "cancelled");

        assertEquals("CANCELLED", response.getStatus());
        verify(inventoryGrpcClient).releaseReservation(7L, 2001L, 1);
        verify(orderRepository).save(order);
        verify(orderEventPublisher).publishOrderStatusChanged(order, OrderStatus.RESERVED);
    }

    @Test
    void changeStatusShouldThrowForUnsupportedStatus() {
        Order order = buildOrder(7L, OrderStatus.NEW);
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> orderService.changeStatus(7L, "WRONG_STATUS"));
        assertTrue(ex.getMessage().contains("Unsupported status"));
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void changeStatusShouldThrowForInvalidTransition() {
        Order order = buildOrder(7L, OrderStatus.COMPLETED);
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> orderService.changeStatus(7L, "CANCELLED"));
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
