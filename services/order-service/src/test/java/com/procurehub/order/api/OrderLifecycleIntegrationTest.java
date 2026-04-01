package com.procurehub.order.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.procurehub.grpc.inventory.v1.CheckAndReserveResponse;
import com.procurehub.inventory.avro.InventoryReservedEvent;
import com.procurehub.order.api.dto.CreateOrderRequest;
import com.procurehub.order.api.dto.UpdateOrderStatusRequest;
import com.procurehub.order.client.InventoryGrpcClient;
import com.procurehub.order.event.OrderEventPublisher;
import com.procurehub.order.kafka.InventoryEventsListener;
import com.procurehub.order.support.TestJwtFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderLifecycleIntegrationTest {

    private static final String USER_AUTHORIZATION = "Bearer " + TestJwtFactory.userToken();
    private static final String OTHER_USER_AUTHORIZATION =
            "Bearer " + TestJwtFactory.userToken("another-user@example.com");
    private static final String ADMIN_AUTHORIZATION = "Bearer " + TestJwtFactory.adminToken();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InventoryEventsListener inventoryEventsListener;

    @MockBean
    private OrderEventPublisher orderEventPublisher;

    @TestConfiguration
    static class TestInventoryClientConfig {
        @Bean
        @Primary
        InventoryGrpcClient inventoryGrpcClient() {
            return new InventoryGrpcClient(null) {
                @Override
                public CheckAndReserveResponse checkAndReserve(long orderId, long productId, int quantity) {
                    return CheckAndReserveResponse.newBuilder()
                            .setSuccess(true)
                            .setProductId(productId)
                            .setAvailableQuantity(25)
                            .setReservedQuantity(quantity)
                            .setMessage("reserved")
                            .setOrderId(orderId)
                            .build();
                }
            };
        }
    }

    @Test
    void shouldCompleteOrderLifecycleAfterReservationConfirmation() throws Exception {
        CreateOrderRequest createRequest = new CreateOrderRequest();
        createRequest.setUserId(101L);
        createRequest.setProductId(2001L);
        createRequest.setQuantity(1);

        MvcResult createResult = mockMvc.perform(post("/orders")
                        .header("Authorization", USER_AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESERVATION_PENDING"))
                .andExpect(jsonPath("$.statusMessage").value("Reservation requested"))
                .andReturn();

        JsonNode createJson = objectMapper.readTree(createResult.getResponse().getContentAsString());
        long orderId = createJson.get("id").asLong();

        confirmReservation(orderId, 2001L, 1);

        mockMvc.perform(get("/orders/{orderId}", orderId)
                        .header("Authorization", USER_AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId))
                .andExpect(jsonPath("$.status").value("RESERVED"))
                .andExpect(jsonPath("$.statusMessage").value("Inventory reserved"));

        patchStatus(orderId, "PAID", "Status changed: RESERVED -> PAID");
        patchStatus(orderId, "SHIPPED", "Status changed: PAID -> SHIPPED");
        patchStatus(orderId, "COMPLETED", "Status changed: SHIPPED -> COMPLETED");

        mockMvc.perform(get("/orders/{orderId}", orderId)
                        .header("Authorization", USER_AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.statusMessage").value("Status changed: SHIPPED -> COMPLETED"));
    }

    @Test
    void shouldRestrictRoleUserToOwnOrders() throws Exception {
        CreateOrderRequest createRequest = new CreateOrderRequest();
        createRequest.setUserId(101L);
        createRequest.setProductId(2002L);
        createRequest.setQuantity(2);

        MvcResult createResult = mockMvc.perform(post("/orders")
                        .header("Authorization", USER_AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode createJson = objectMapper.readTree(createResult.getResponse().getContentAsString());
        long orderId = createJson.get("id").asLong();

        MvcResult ownOrdersResult = mockMvc.perform(get("/orders")
                        .header("Authorization", USER_AUTHORIZATION))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult otherOrdersResult = mockMvc.perform(get("/orders")
                        .header("Authorization", OTHER_USER_AUTHORIZATION))
                .andExpect(status().isOk())
                .andReturn();

        assertTrue(readOrderIds(ownOrdersResult).contains(orderId));
        assertFalse(readOrderIds(otherOrdersResult).contains(orderId));

        mockMvc.perform(get("/orders/{orderId}", orderId)
                        .header("Authorization", OTHER_USER_AUTHORIZATION))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/orders/{orderId}", orderId)
                        .header("Authorization", ADMIN_AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId));
    }

    private void confirmReservation(long orderId, long productId, int quantity) {
        InventoryReservedEvent event = InventoryReservedEvent.newBuilder()
                .setOrderId(orderId)
                .setProductId(productId)
                .setQuantity(quantity)
                .setAvailableQuantity(25)
                .setReservedQuantity(quantity)
                .setMessage("reserved")
                .setReservedAt(LocalDateTime.now().toString())
                .build();

        inventoryEventsListener.onInventoryReserved(event);
    }

    private void patchStatus(long orderId, String newStatus, String expectedMessage) throws Exception {
        UpdateOrderStatusRequest request = new UpdateOrderStatusRequest();
        request.setStatus(newStatus);

        mockMvc.perform(patch("/orders/{orderId}/status", orderId)
                        .header("Authorization", ADMIN_AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId))
                .andExpect(jsonPath("$.status").value(newStatus))
                .andExpect(jsonPath("$.statusMessage").value(expectedMessage));
    }

    private List<Long> readOrderIds(MvcResult result) throws Exception {
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        List<Long> ids = new ArrayList<>();
        for (JsonNode item : json) {
            ids.add(item.get("id").asLong());
        }
        return ids;
    }
}
