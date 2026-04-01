package com.procurehub.gateway.config;

import com.procurehub.grpc.product.v1.ProductServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProductGrpcClientConfig {

    @Bean(destroyMethod = "shutdownNow")
    public ManagedChannel productManagedChannel(
            @Value("${product.grpc.host}") String host,
            @Value("${product.grpc.port}") int port
    ) {
        return ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
    }

    @Bean
    public ProductServiceGrpc.ProductServiceBlockingStub productBlockingStub(ManagedChannel productManagedChannel) {
        return ProductServiceGrpc.newBlockingStub(productManagedChannel);
    }
}
