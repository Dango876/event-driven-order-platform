package com.procurehub.gateway.api;

import com.procurehub.gateway.api.dto.ProductGrpcResponse;
import com.procurehub.gateway.client.ProductGrpcClient;
import io.grpc.StatusRuntimeException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
public class GrpcProductController {

    private final ProductGrpcClient productGrpcClient;

    public GrpcProductController(ProductGrpcClient productGrpcClient) {
        this.productGrpcClient = productGrpcClient;
    }

    @GetMapping("/api/grpc/products/{productId}")
    public Mono<ProductGrpcResponse> getProductById(@PathVariable("productId") String productId) {
        return Mono.fromCallable(() -> productGrpcClient.getProductById(productId))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(StatusRuntimeException.class, this::mapGrpcError);
    }

    private ResponseStatusException mapGrpcError(StatusRuntimeException ex) {
        HttpStatus status = switch (ex.getStatus().getCode()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INVALID_ARGUMENT -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.BAD_GATEWAY;
        };

        String message = ex.getStatus().getDescription();
        if (message == null || message.isBlank()) {
            message = "Product gRPC call failed";
        }

        return new ResponseStatusException(status, message, ex);
    }
}
