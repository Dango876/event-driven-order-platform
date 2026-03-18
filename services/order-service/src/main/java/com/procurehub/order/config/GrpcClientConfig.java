package com.procurehub.order.config;

import com.procurehub.grpc.inventory.v1.InventoryServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GrpcClientConfig {

    @Bean(destroyMethod = "shutdownNow")
    public ManagedChannel inventoryManagedChannel(
            @Value("${inventory.grpc.host}") String host,
            @Value("${inventory.grpc.port}") int port
    ) {
        return ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
    }

    @Bean
    public InventoryServiceGrpc.InventoryServiceBlockingStub inventoryBlockingStub(ManagedChannel inventoryManagedChannel) {
        return InventoryServiceGrpc.newBlockingStub(inventoryManagedChannel);
    }
}
