package com.procurehub.inventory.grpc;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
public class GrpcServerLifecycle {

    private final InventoryGrpcService inventoryGrpcService;
    private final int grpcPort;

    private Server server;

    public GrpcServerLifecycle(
            InventoryGrpcService inventoryGrpcService,
            @Value("${grpc.server.port:9091}") int grpcPort
    ) {
        this.inventoryGrpcService = inventoryGrpcService;
        this.grpcPort = grpcPort;
    }

    @PostConstruct
    public void start() {
        try {
            this.server = ServerBuilder.forPort(grpcPort)
                    .addService(inventoryGrpcService)
                    .build()
                    .start();
            System.out.println("gRPC server started on port " + grpcPort);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start gRPC server", e);
        }
    }

    @PreDestroy
    public void stop() {
        if (server != null) {
            server.shutdown();
            try {
                server.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
