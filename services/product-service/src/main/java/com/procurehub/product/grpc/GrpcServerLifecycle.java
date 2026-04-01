package com.procurehub.product.grpc;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
public class GrpcServerLifecycle {

    private static final Logger log = LoggerFactory.getLogger(GrpcServerLifecycle.class);

    private final ProductGrpcService productGrpcService;
    private final int grpcPort;

    private Server server;

    public GrpcServerLifecycle(
            ProductGrpcService productGrpcService,
            @Value("${grpc.server.port:9094}") int grpcPort
    ) {
        this.productGrpcService = productGrpcService;
        this.grpcPort = grpcPort;
    }

    @PostConstruct
    public void start() {
        try {
            this.server = ServerBuilder.forPort(grpcPort)
                    .addService(productGrpcService)
                    .build()
                    .start();
            log.info("Product gRPC server started on port {}", grpcPort);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to start product gRPC server", ex);
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
