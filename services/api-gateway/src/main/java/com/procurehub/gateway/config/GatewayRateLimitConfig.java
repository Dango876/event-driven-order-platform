package com.procurehub.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

@Configuration
public class GatewayRateLimitConfig {

    @Bean
    public RedisRateLimiter redisRateLimiter(
            @Value("${app.rate-limit.replenish-rate:10}") int replenishRate,
            @Value("${app.rate-limit.burst-capacity:20}") int burstCapacity,
            @Value("${app.rate-limit.requested-tokens:1}") int requestedTokens
    ) {
        return new RedisRateLimiter(replenishRate, burstCapacity, requestedTokens);
    }

    @Bean
    public KeyResolver remoteAddressKeyResolver() {
        return exchange -> Mono.just(resolveClientKey(exchange));
    }

    private String resolveClientKey(ServerWebExchange exchange) {
        String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            String clientIp = forwardedFor.split(",")[0].trim();
            if (StringUtils.hasText(clientIp)) {
                return "ip:" + clientIp;
            }
        }

        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return "ip:" + remoteAddress.getAddress().getHostAddress();
        }

        return "ip:unknown";
    }
}
