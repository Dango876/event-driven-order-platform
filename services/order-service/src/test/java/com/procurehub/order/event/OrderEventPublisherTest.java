package com.procurehub.order.event;

import com.procurehub.order.avro.OrderCreatedEvent;
import com.procurehub.order.avro.OrderStatusChangedEvent;
import com.procurehub.order.model.Order;
import com.procurehub.order.model.OrderStatus;
import org.apache.avro.specific.SpecificRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderEventPublisherTest {

    @Mock
    private KafkaTemplate<String, SpecificRecord> kafkaTemplate;

    @Test
    void publishOrderCreatedShouldSendAvroEvent() {
        OrderEventPublisher publisher = new OrderEventPublisher(kafkaTemplate, "order.created", "order.status-changed");
        Order order = sampleOrder(12L, OrderStatus.NEW);

        when(kafkaTemplate.send(eq("order.created"), eq("12"), any(SpecificRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishOrderCreated(order);

        ArgumentCaptor<SpecificRecord> eventCaptor = ArgumentCaptor.forClass(SpecificRecord.class);
        verify(kafkaTemplate).send(eq("order.created"), eq("12"), eventCaptor.capture());
        assertTrue(eventCaptor.getValue() instanceof OrderCreatedEvent);
    }

    @Test
    void publishOrderStatusChangedShouldSendAvroEvent() {
        OrderEventPublisher publisher = new OrderEventPublisher(kafkaTemplate, "order.created", "order.status-changed");
        Order order = sampleOrder(12L, OrderStatus.PAID);

        when(kafkaTemplate.send(eq("order.status-changed"), eq("12"), any(SpecificRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishOrderStatusChanged(order, OrderStatus.RESERVED);

        ArgumentCaptor<SpecificRecord> eventCaptor = ArgumentCaptor.forClass(SpecificRecord.class);
        verify(kafkaTemplate).send(eq("order.status-changed"), eq("12"), eventCaptor.capture());
        assertTrue(eventCaptor.getValue() instanceof OrderStatusChangedEvent);
    }

    @Test
    void publishOrderCreatedShouldThrowWhenSendFails() {
        OrderEventPublisher publisher = new OrderEventPublisher(kafkaTemplate, "order.created", "order.status-changed");
        Order order = sampleOrder(15L, OrderStatus.NEW);

        CompletableFuture<SendResult<String, SpecificRecord>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("kafka unavailable"));
        when(kafkaTemplate.send(eq("order.created"), eq("15"), any(SpecificRecord.class))).thenReturn(failed);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> publisher.publishOrderCreated(order));
        assertTrue(ex.getMessage().contains("Failed to publish order.created"));
    }

    private Order sampleOrder(Long id, OrderStatus status) {
        Order order = new Order();
        order.setUserId(101L);
        order.setProductId(2001L);
        order.setQuantity(1);
        order.setStatus(status);
        order.setStatusMessage("message");
        ReflectionTestUtils.setField(order, "id", id);
        ReflectionTestUtils.setField(order, "createdAt", LocalDateTime.now());
        ReflectionTestUtils.setField(order, "updatedAt", LocalDateTime.now());
        assertEquals(id, order.getId());
        return order;
    }
}

