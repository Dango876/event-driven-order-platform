package com.procurehub.order.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.procurehub.grpc.inventory.v1.CheckAndReserveResponse;
import com.procurehub.order.api.dto.CreateOrderRequest;
import com.procurehub.order.api.dto.UpdateOrderStatusRequest;
import com.procurehub.order.client.InventoryGrpcClient;
import com.procurehub.order.event.OrderEventPublisher;
import com.procurehub.order.support.TestJwtFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

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
    private static final String ADMIN_AUTHORIZATION = "Bearer " + TestJwtFactory.adminToken();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    void shouldCompleteOrderLifecycleFromReservedToCompleted() throws Exception {
        CreateOrderRequest createRequest = new CreateOrderRequest();
        createRequest.setUserId(101L);
        createRequest.setProductId(2001L);
        createRequest.setQuantity(1);

        MvcResult createResult = mockMvc.perform(post("/orders")
                        .header("Authorization", USER_AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESERVED"))
                .andExpect(jsonPath("$.statusMessage").value("Inventory reserved"))
                .andReturn();

        JsonNode createJson = objectMapper.readTree(createResult.getResponse().getContentAsString());
        long orderId = createJson.get("id").asLong();

        patchStatus(orderId, "PAID", "Status changed: RESERVED -> PAID");
        patchStatus(orderId, "SHIPPED", "Status changed: PAID -> SHIPPED");
        patchStatus(orderId, "COMPLETED", "Status changed: SHIPPED -> COMPLETED");

        mockMvc.perform(get("/orders/{orderId}", orderId))
                .header("Authorization", USER_AUTHORIZATION)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.statusMessage").value("Status changed: SHIPPED -> COMPLETED"));
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
}
