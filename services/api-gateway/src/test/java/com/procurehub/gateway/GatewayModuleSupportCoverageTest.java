package com.procurehub.gateway;

import com.procurehub.gateway.config.GatewayRateLimitConfig;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.test.web.reactive.server.MockServerConfigurer;
import org.springframework.web.server.adapter.DefaultServerWebExchange;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
}
