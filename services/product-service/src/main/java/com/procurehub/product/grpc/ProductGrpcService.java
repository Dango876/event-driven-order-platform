package com.procurehub.product.grpc;

import com.procurehub.grpc.product.v1.GetProductByIdRequest;
import com.procurehub.grpc.product.v1.GetProductByIdResponse;
import com.procurehub.grpc.product.v1.ProductServiceGrpc;
import com.procurehub.product.dto.ProductResponse;
import com.procurehub.product.exception.NotFoundException;
import com.procurehub.product.service.ProductService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Component;

@Component
public class ProductGrpcService extends ProductServiceGrpc.ProductServiceImplBase {

    private final ProductService productService;

    public ProductGrpcService(ProductService productService) {
        this.productService = productService;
    }

    @Override
    public void getProductById(GetProductByIdRequest request, StreamObserver<GetProductByIdResponse> responseObserver) {
        try {
            ProductResponse product = productService.getById(request.getProductId());

            GetProductByIdResponse response = GetProductByIdResponse.newBuilder()
                    .setId(product.getId())
                    .setName(safe(product.getName()))
                    .setDescription(safe(product.getDescription()))
                    .setCategory(safe(product.getCategory()))
                    .setPrice(product.getPrice() == null ? "" : product.getPrice().toPlainString())
                    .setPublished(product.isPublished())
                    .setCreatedAt(product.getCreatedAt() == null ? "" : product.getCreatedAt().toString())
                    .setUpdatedAt(product.getUpdatedAt() == null ? "" : product.getUpdatedAt().toString())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (NotFoundException ex) {
            responseObserver.onError(Status.NOT_FOUND.withDescription(ex.getMessage()).asRuntimeException());
        } catch (Exception ex) {
            responseObserver.onError(Status.INTERNAL.withDescription("Internal server error").asRuntimeException());
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
