package com.procurehub.gateway.client;

import com.procurehub.gateway.api.dto.ProductGrpcResponse;
import com.procurehub.grpc.product.v1.GetProductByIdRequest;
import com.procurehub.grpc.product.v1.GetProductByIdResponse;
import com.procurehub.grpc.product.v1.ProductServiceGrpc;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

@Component
public class ProductGrpcClient {

    private final ProductServiceGrpc.ProductServiceBlockingStub stub;

    public ProductGrpcClient(ProductServiceGrpc.ProductServiceBlockingStub stub) {
        this.stub = stub;
    }

    public ProductGrpcResponse getProductById(String productId) {
        GetProductByIdResponse response = stub.getProductById(
                GetProductByIdRequest.newBuilder()
                        .setProductId(productId)
                        .build()
        );

        ProductGrpcResponse product = new ProductGrpcResponse();
        product.setId(response.getId());
        product.setName(emptyToNull(response.getName()));
        product.setDescription(emptyToNull(response.getDescription()));
        product.setCategory(emptyToNull(response.getCategory()));
        product.setPrice(parsePrice(response.getPrice()));
        product.setPublished(response.getPublished());
        product.setCreatedAt(parseDateTime(response.getCreatedAt()));
        product.setUpdatedAt(parseDateTime(response.getUpdatedAt()));
        return product;
    }

    private BigDecimal parsePrice(String value) {
        return StringUtils.hasText(value) ? new BigDecimal(value) : null;
    }

    private LocalDateTime parseDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }
}
