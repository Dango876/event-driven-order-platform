package com.procurehub.order.api;

import com.procurehub.grpc.inventory.v1.CheckAndReserveResponse;
import com.procurehub.grpc.inventory.v1.ReleaseReservationResponse;
import com.procurehub.inventory.avro.InventoryReservedEvent;
import com.procurehub.order.api.dto.CreateOrderRequest;
import com.procurehub.order.api.dto.UpdateOrderStatusRequest;
import com.procurehub.order.client.InventoryGrpcClient;
import com.procurehub.order.event.OrderEventPublisher;
import com.procurehub.order.kafka.InventoryEventsListener;
import com.procurehub.order.support.TestJwtFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.bootstrap-servers=localhost:9092",
        "management.tracing.sampling.probability=0.0"
})
class OrderLifecycleWebTestClientIT {

    private static final String USER_AUTHORIZATION = "Bearer " + TestJwtFactory.userToken();
    private static final String ADMIN_AUTHORIZATION = "Bearer " + TestJwtFactory.adminToken();

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("order_db")
                    .withUsername("order_user")
                    .withPassword("order_pass");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private InventoryEventsListener inventoryEventsListener;

    @MockBean
    private InventoryGrpcClient inventoryGrpcClient;

    @MockBean
    private OrderEventPublisher orderEventPublisher;

    @BeforeEach
    void setupMocks() {
        Mockito.when(inventoryGrpcClient.checkAndReserve(Mockito.anyLong(), Mockito.anyLong(), Mockito.anyInt()))
                .thenAnswer(inv -> {
                    long orderId = inv.getArgument(0);
                    long productId = inv.getArgument(1);
                    int quantity = inv.getArgument(2);
                    return CheckAndReserveResponse.newBuilder()
                            .setSuccess(true)
                            .setOrderId(orderId)
                            .setProductId(productId)
                            .setAvailableQuantity(100)
                            .setReservedQuantity(quantity)
                            .setMessage("reserved")
                            .build();
                });

        Mockito.when(inventoryGrpcClient.releaseReservation(Mockito.anyLong(), Mockito.anyLong(), Mockito.anyInt()))
                .thenAnswer(inv -> {
                    long orderId = inv.getArgument(0);
                    long productId = inv.getArgument(1);
                    int quantity = inv.getArgument(2);
                    return ReleaseReservationResponse.newBuilder()
                            .setSuccess(true)
                            .setOrderId(orderId)
                            .setProductId(productId)
                            .setAvailableQuantity(100)
                            .setReservedQuantity(Math.max(0, quantity - 1))
                            .setMessage("released")
                            .build();
                });
    }

    @Test
    void shouldCompleteOrderLifecycleViaApiWithWebTestClient() {
        CreateOrderRequest create = new CreateOrderRequest();
        create.setUserId(101L);
        create.setProductId(2001L);
        create.setQuantity(1);

        EntityExchangeResult<Map> createdResult = webTestClient.post()
                .uri("/orders")
                .header("Authorization", USER_AUTHORIZATION)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(create)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .value(body -> {
                    assertNotNull(body);
                    assertEquals("RESERVATION_PENDING", body.get("status"));
                    assertEquals("Reservation requested", body.get("statusMessage"));
                })
                .returnResult();

        @SuppressWarnings("unchecked")
        Map<String, Object> created = createdResult.getResponseBody();
        assertNotNull(created);
        Long orderId = ((Number) created.get("id")).longValue();

        confirmReservation(orderId, 2001L, 1);

        webTestClient.get()
                .uri("/orders/{id}", orderId)
                .header("Authorization", USER_AUTHORIZATION)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(orderId.intValue())
                .jsonPath("$.status").isEqualTo("RESERVED")
                .jsonPath("$.statusMessage").isEqualTo("Inventory reserved");

        patchStatus(orderId, "PAID", "Status changed: RESERVED -> PAID");
        patchStatus(orderId, "SHIPPED", "Status changed: PAID -> SHIPPED");
        patchStatus(orderId, "COMPLETED", "Status changed: SHIPPED -> COMPLETED");

        webTestClient.get()
                .uri("/orders/{id}", orderId)
                .header("Authorization", USER_AUTHORIZATION)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(orderId.intValue())
                .jsonPath("$.status").isEqualTo("COMPLETED")
                .jsonPath("$.statusMessage").isEqualTo("Status changed: SHIPPED -> COMPLETED");
    }

    private void confirmReservation(Long orderId, Long productId, int quantity) {
        InventoryReservedEvent event = InventoryReservedEvent.newBuilder()
                .setOrderId(orderId)
                .setProductId(productId)
                .setQuantity(quantity)
                .setAvailableQuantity(100)
                .setReservedQuantity(quantity)
                .setMessage("reserved")
                .setReservedAt(LocalDateTime.now().toString())
                .build();

        inventoryEventsListener.onInventoryReserved(event);
    }

    private void patchStatus(Long orderId, String newStatus, String expectedMessage) {
        UpdateOrderStatusRequest request = new UpdateOrderStatusRequest();
        request.setStatus(newStatus);

        webTestClient.patch()
                .uri("/orders/{id}/status", orderId)
                .header("Authorization", ADMIN_AUTHORIZATION)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(orderId.intValue())
                .jsonPath("$.status").isEqualTo(newStatus)
                .jsonPath("$.statusMessage").isEqualTo(expectedMessage);
    }
}
