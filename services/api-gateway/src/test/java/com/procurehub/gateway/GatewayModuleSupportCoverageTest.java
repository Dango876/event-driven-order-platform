package com.procurehub.gateway;

import com.procurehub.gateway.api.GrpcProductController;
import com.procurehub.gateway.api.dto.ProductGrpcResponse;
import com.procurehub.gateway.client.ProductGrpcClient;
import com.procurehub.gateway.config.GatewaySecurityConfig;
import com.procurehub.gateway.config.GatewayRateLimitConfig;
import com.procurehub.grpc.product.v1.GetProductByIdResponse;
import com.procurehub.grpc.product.v1.ProductServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GatewayModuleSupportCoverageTest {

    @Test
    void rateLimiterBeanShouldBeCreated() {
        GatewayRateLimitConfig config = new GatewayRateLimitConfig();

        assertNotNull(config.redisRateLimiter(10, 20, 1));
    }

    @Test
    void keyResolverShouldPreferForwardedHeader() {
        GatewayRateLimitConfig config = new GatewayRateLimitConfig();
        KeyResolver resolver = config.remoteAddressKeyResolver();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/")
                        .header("X-Forwarded-For", "203.0.113.7, 10.0.0.2")
                        .build()
        );

        assertEquals("ip:203.0.113.7", resolver.resolve(exchange).block());
    }

    @Test
    void keyResolverShouldFallbackToRemoteAddressAndUnknown() {
        GatewayRateLimitConfig config = new GatewayRateLimitConfig();
        KeyResolver resolver = config.remoteAddressKeyResolver();

        MockServerWebExchange remoteExchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/")
                        .remoteAddress(new InetSocketAddress("198.51.100.9", 8080))
                        .build()
        );

        MockServerWebExchange unknownExchange = MockServerWebExchange.from(MockServerHttpRequest.get("/").build());

        assertEquals("ip:198.51.100.9", resolver.resolve(remoteExchange).block());
        assertEquals("ip:unknown", resolver.resolve(unknownExchange).block());
    }

    @Test
    void grpcClientShouldMapResponseFields() throws Exception {
        Server server = ServerBuilder.forPort(0)
                .addService(new ProductServiceGrpc.ProductServiceImplBase() {
                    @Override
                    public void getProductById(
                            com.procurehub.grpc.product.v1.GetProductByIdRequest request,
                            StreamObserver<GetProductByIdResponse> responseObserver
                    ) {
                        responseObserver.onNext(
                                GetProductByIdResponse.newBuilder()
                                        .setId(request.getProductId())
                                        .setName("Keyboard")
                                        .setDescription("Mechanical")
                                        .setCategory("peripherals")
                                        .setPrice("99.90")
                                        .setPublished(true)
                                        .setCreatedAt("2026-04-01T10:15:30")
                                        .setUpdatedAt("2026-04-01T11:15:30")
                                        .build()
                        );
                        responseObserver.onCompleted();
                    }
                })
                .build()
                .start();

        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", server.getPort())
                .usePlaintext()
                .build();

        try {
            ProductGrpcClient client = new ProductGrpcClient(ProductServiceGrpc.newBlockingStub(channel));
            ProductGrpcResponse response = client.getProductById("p-1");

            assertEquals("p-1", response.getId());
            assertEquals("Keyboard", response.getName());
            assertEquals("99.90", response.getPrice().toPlainString());
            assertEquals(true, response.isPublished());
        } finally {
            channel.shutdownNow();
            server.shutdownNow();
        }
    }

    @Test
    void grpcControllerShouldReturnProductAndTranslateGrpcErrors() {
        ProductGrpcResponse response = new ProductGrpcResponse();
        response.setId("p-1");
        ProductGrpcClient client = new ProductGrpcClient(null) {
            @Override
            public ProductGrpcResponse getProductById(String productId) {
                if ("missing".equals(productId)) {
                    throw Status.NOT_FOUND.withDescription("missing").asRuntimeException();
                }
                return response;
            }
        };
        GrpcProductController controller = new GrpcProductController(client);

        assertEquals("p-1", controller.getProductById("p-1").block().getId());

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> controller.getProductById("missing").block()
        );

        assertEquals(404, ex.getStatusCode().value());
        assertEquals("missing", ex.getReason());
    }

    @Test
    void grpcControllerRouteShouldBindPathVariable() {
        ProductGrpcResponse response = new ProductGrpcResponse();
        response.setId("p-42");
        response.setName("Bound product");

        ProductGrpcClient client = new ProductGrpcClient(null) {
            @Override
            public ProductGrpcResponse getProductById(String productId) {
                response.setId(productId);
                return response;
            }
        };

        WebTestClient webTestClient = WebTestClient.bindToController(new GrpcProductController(client)).build();

        webTestClient.get()
                .uri("/api/grpc/products/p-42")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("p-42")
                .jsonPath("$.name").isEqualTo("Bound product");
    }

    @Test
    void gatewaySecurityBeansShouldExposeJwtAuthorities() {
        GatewaySecurityConfig config = new GatewaySecurityConfig();
        ReflectionTestUtils.setField(
                config,
                "jwtSecret",
                Base64.getEncoder().encodeToString("01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8))
        );

        assertNotNull(config.jwtDecoder());

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("user@example.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("roles", List.of("ROLE_USER"))
                .build();

        assertEquals(
                "ROLE_USER",
                config.jwtAuthenticationConverter().convert(jwt).block().getAuthorities().iterator().next().getAuthority()
        );
    }
}
