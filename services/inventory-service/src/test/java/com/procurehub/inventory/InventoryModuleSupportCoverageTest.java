package com.procurehub.inventory;

import com.procurehub.grpc.inventory.v1.CheckAndReserveRequest;
import com.procurehub.grpc.inventory.v1.CheckAndReserveResponse;
import com.procurehub.grpc.inventory.v1.GetStockRequest;
import com.procurehub.grpc.inventory.v1.GetStockResponse;
import com.procurehub.grpc.inventory.v1.ReleaseReservationRequest;
import com.procurehub.grpc.inventory.v1.ReleaseReservationResponse;
import com.procurehub.inventory.api.HealthController;
import com.procurehub.inventory.api.InventoryController;
import com.procurehub.inventory.api.dto.CreateInventoryItemRequest;
import com.procurehub.inventory.api.dto.InventoryItemResponse;
import com.procurehub.inventory.api.dto.ReserveRequest;
import com.procurehub.inventory.api.error.ApiError;
import com.procurehub.inventory.api.error.GlobalExceptionHandler;
import com.procurehub.inventory.event.InventoryEventPublisher;
import com.procurehub.inventory.grpc.InventoryGrpcService;
import com.procurehub.inventory.kafka.OrderCreatedEventsListener;
import com.procurehub.inventory.model.InventoryItem;
import com.procurehub.inventory.service.DistributedLockException;
import com.procurehub.inventory.service.InventoryService;
import com.procurehub.inventory.service.NotEnoughStockException;
import com.procurehub.inventory.service.OrderCreatedReservationService;
import com.procurehub.inventory.service.RedisDistributedLockService;
import com.procurehub.order.avro.OrderCreatedEvent;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryModuleSupportCoverageTest {

    @Mock
    private InventoryService inventoryService;

    @Mock
    private KafkaTemplate<String, org.apache.avro.specific.SpecificRecord> kafkaTemplate;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Test
    void dtoModelHealthAndErrorTypesShouldWork() {
        CreateInventoryItemRequest create = new CreateInventoryItemRequest();
        create.setProductId(10L);
        create.setAvailableQuantity(20);
        assertEquals(10L, create.getProductId());

        ReserveRequest reserve = new ReserveRequest();
        reserve.setProductId(10L);
        reserve.setQuantity(3);
        assertEquals(3, reserve.getQuantity());

        InventoryItemResponse response = new InventoryItemResponse();
        LocalDateTime now = LocalDateTime.now();
        response.setId(1L);
        response.setProductId(10L);
        response.setAvailableQuantity(20);
        response.setReservedQuantity(5);
        response.setFreeQuantity(15);
        response.setCreatedAt(now);
        response.setUpdatedAt(now);
        assertEquals(15, response.getFreeQuantity());

        InventoryItem item = new InventoryItem();
        ReflectionTestUtils.setField(item, "id", 1L);
        item.setProductId(10L);
        item.setAvailableQuantity(20);
        item.setReservedQuantity(5);
        ReflectionTestUtils.invokeMethod(item, "onCreate");
        ReflectionTestUtils.invokeMethod(item, "onUpdate");
        assertEquals(10L, item.getProductId());

        ApiError error = new ApiError(now, 409, "conflict");
        assertEquals(409, error.status());
        assertEquals("conflict", error.message());

        assertEquals("ok", new HealthController().health().get("status"));
        assertEquals("lock", new DistributedLockException("lock").getMessage());
        assertEquals("stock", new NotEnoughStockException("stock").getMessage());
    }

    @Test
    void controllerAndExceptionHandlerShouldWork() {
        InventoryController controller = new InventoryController(inventoryService);
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        InventoryItemResponse response = new InventoryItemResponse();
        CreateInventoryItemRequest create = new CreateInventoryItemRequest();
        ReserveRequest reserve = new ReserveRequest();
        reserve.setProductId(10L);
        reserve.setQuantity(2);

        when(inventoryService.createItem(create)).thenReturn(response);
        when(inventoryService.getByProductId(10L)).thenReturn(response);
        when(inventoryService.reserve(10L, 2)).thenReturn(response);
        when(inventoryService.release(10L, 2)).thenReturn(response);
        when(inventoryService.updateAvailable(10L, 7)).thenReturn(response);

        assertEquals(response, controller.createItem(create));
        assertEquals(response, controller.getItem(10L));
        assertEquals(response, controller.reserve(reserve));
        assertEquals(response, controller.release(reserve));
        assertEquals(response, controller.updateAvailable(10L, 7));

        ResponseEntity<ApiError> notEnough = handler.handleNotEnough(new NotEnoughStockException("not enough"));
        ResponseEntity<ApiError> badRequest = handler.handleBadRequest(new IllegalArgumentException("bad"));
        ResponseEntity<ApiError> locked = handler.handleLockUnavailable(new DistributedLockException("lock"));
        ResponseEntity<ApiError> other = handler.handleOther(new RuntimeException("boom"));

        assertEquals(HttpStatus.CONFLICT, notEnough.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, badRequest.getStatusCode());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, locked.getStatusCode());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, other.getStatusCode());
    }

    @Test
    void eventPublisherShouldSkipInvalidOrdersAndPublishValidOnes() {
        InventoryEventPublisher publisher = new InventoryEventPublisher(kafkaTemplate, "inventory.reserved", "inventory.reserve-failed");
        when(kafkaTemplate.send(eq("inventory.reserved"), eq("7"), any())).thenReturn(CompletableFuture.completedFuture(null));
        when(kafkaTemplate.send(eq("inventory.reserve-failed"), eq("8"), any())).thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishInventoryReserved(0L, 10L, 1, 20, 1, "ignored");
        publisher.publishInventoryReservationFailed(0L, 10L, 1, "ignored");
        verify(kafkaTemplate, never()).send(eq("inventory.reserved"), eq("0"), any());

        publisher.publishInventoryReserved(7L, 10L, 2, 20, 2, "reserved");
        publisher.publishInventoryReservationFailed(8L, 10L, 2, "failed");

        verify(kafkaTemplate).send(eq("inventory.reserved"), eq("7"), any());
        verify(kafkaTemplate).send(eq("inventory.reserve-failed"), eq("8"), any());
    }

    @Test
    void grpcServiceShouldHandleSuccessAndErrorScenarios() {
        InventoryEventPublisher publisher = mock(InventoryEventPublisher.class);
        InventoryGrpcService grpcService = new InventoryGrpcService(inventoryService, publisher);
        @SuppressWarnings("unchecked")
        StreamObserver<CheckAndReserveResponse> reserveObserver = mock(StreamObserver.class);
        @SuppressWarnings("unchecked")
        StreamObserver<ReleaseReservationResponse> releaseObserver = mock(StreamObserver.class);
        @SuppressWarnings("unchecked")
        StreamObserver<GetStockResponse> stockObserver = mock(StreamObserver.class);

        InventoryItemResponse response = new InventoryItemResponse();
        response.setProductId(10L);
        response.setAvailableQuantity(20);
        response.setReservedQuantity(3);
        response.setFreeQuantity(17);

        when(inventoryService.reserve(10L, 3)).thenReturn(response);
        when(inventoryService.release(10L, 1)).thenReturn(response);
        when(inventoryService.getByProductId(10L)).thenReturn(response);

        grpcService.checkAndReserve(CheckAndReserveRequest.newBuilder().setOrderId(5L).setProductId(10L).setQuantity(3).build(), reserveObserver);
        grpcService.releaseReservation(ReleaseReservationRequest.newBuilder().setOrderId(5L).setProductId(10L).setQuantity(1).build(), releaseObserver);
        grpcService.getStock(GetStockRequest.newBuilder().setProductId(10L).build(), stockObserver);

        verify(publisher).publishInventoryReserved(5L, 10L, 3, 20, 3, "reserved");
        verify(reserveObserver).onCompleted();
        verify(releaseObserver).onCompleted();
        verify(stockObserver).onCompleted();

        when(inventoryService.reserve(11L, 10)).thenThrow(new NotEnoughStockException("not enough"));
        grpcService.checkAndReserve(CheckAndReserveRequest.newBuilder().setOrderId(6L).setProductId(11L).setQuantity(10).build(), reserveObserver);
        verify(publisher).publishInventoryReservationFailed(6L, 11L, 10, "not enough");

        when(inventoryService.release(12L, 1)).thenThrow(new IllegalArgumentException("bad"));
        grpcService.releaseReservation(ReleaseReservationRequest.newBuilder().setOrderId(6L).setProductId(12L).setQuantity(1).build(), releaseObserver);

        when(inventoryService.getByProductId(13L)).thenThrow(new IllegalArgumentException("missing"));
        grpcService.getStock(GetStockRequest.newBuilder().setProductId(13L).build(), stockObserver);
    }

    @Test
    void orderCreatedReservationServiceShouldReserveOnceAndPublishFailureWhenNeeded() {
        InventoryEventPublisher publisher = mock(InventoryEventPublisher.class);
        OrderCreatedReservationService reservationService =
                new OrderCreatedReservationService(inventoryService, publisher, redisTemplate);
        OrderCreatedEventsListener listener = new OrderCreatedEventsListener(reservationService);
        InventoryItemResponse reservedItem = new InventoryItemResponse();
        reservedItem.setProductId(10L);
        reservedItem.setAvailableQuantity(20);
        reservedItem.setReservedQuantity(2);
        OrderCreatedEvent event = OrderCreatedEvent.newBuilder()
                .setOrderId(7L)
                .setUserId(101L)
                .setProductId(10L)
                .setQuantity(2)
                .setStatus("RESERVATION_PENDING")
                .setCreatedAt(LocalDateTime.now().toString())
                .build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("inventory:order-created:processed:7"), eq("PROCESSING"), any(Duration.class)))
                .thenReturn(true)
                .thenReturn(false)
                .thenReturn(true);
        when(inventoryService.reserve(10L, 2)).thenReturn(reservedItem);

        listener.onOrderCreated(event);
        verify(publisher).publishInventoryReserved(7L, 10L, 2, 20, 2, "reserved");

        listener.onOrderCreated(event);
        verify(inventoryService).reserve(10L, 2);

        when(inventoryService.reserve(10L, 2)).thenThrow(new NotEnoughStockException("not enough"));
        listener.onOrderCreated(event);
        verify(inventoryService, org.mockito.Mockito.times(2)).reserve(10L, 2);
        verify(publisher).publishInventoryReservationFailed(7L, 10L, 2, "not enough");
    }

    @Test
    void redisDistributedLockServiceShouldSupportAcquireAndFailOpen() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(any(), any(), any(Duration.class))).thenReturn(true);

        RedisDistributedLockService service = new RedisDistributedLockService(redisTemplate);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "keyPrefix", "inventory:redlock:product:");
        ReflectionTestUtils.setField(service, "leaseTimeoutMs", 5000L);
        ReflectionTestUtils.setField(service, "waitTimeoutMs", 1200L);
        ReflectionTestUtils.setField(service, "retryDelayMs", 50L);
        Integer result = service.withProductLock(10L, () -> 42);
        assertEquals(42, result);
        verify(redisTemplate).execute(any(DefaultRedisScript.class), anyList(), any());

        StringRedisTemplate brokenRedisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> brokenOps = mock(ValueOperations.class);
        when(brokenRedisTemplate.opsForValue()).thenReturn(brokenOps);
        when(brokenOps.setIfAbsent(any(), any(), any(Duration.class))).thenThrow(new RuntimeException("redis down"));

        RedisDistributedLockService failOpenService = new RedisDistributedLockService(brokenRedisTemplate);
        ReflectionTestUtils.setField(failOpenService, "enabled", true);
        ReflectionTestUtils.setField(failOpenService, "failOpen", true);
        ReflectionTestUtils.setField(failOpenService, "keyPrefix", "inventory:redlock:product:");
        ReflectionTestUtils.setField(failOpenService, "leaseTimeoutMs", 5000L);
        ReflectionTestUtils.setField(failOpenService, "waitTimeoutMs", 1200L);
        ReflectionTestUtils.setField(failOpenService, "retryDelayMs", 50L);
        assertEquals(7, failOpenService.withProductLock(20L, () -> 7));

        RedisDistributedLockService strictService = new RedisDistributedLockService(brokenRedisTemplate);
        ReflectionTestUtils.setField(strictService, "enabled", true);
        ReflectionTestUtils.setField(strictService, "failOpen", false);
        ReflectionTestUtils.setField(strictService, "keyPrefix", "inventory:redlock:product:");
        ReflectionTestUtils.setField(strictService, "leaseTimeoutMs", 5000L);
        ReflectionTestUtils.setField(strictService, "waitTimeoutMs", 1200L);
        ReflectionTestUtils.setField(strictService, "retryDelayMs", 50L);
        assertThrows(DistributedLockException.class, () -> strictService.withProductLock(30L, () -> 1));
    }
}
