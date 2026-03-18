package com.procurehub.order.client;

import com.procurehub.grpc.inventory.v1.CheckAndReserveRequest;
import com.procurehub.grpc.inventory.v1.CheckAndReserveResponse;
import com.procurehub.grpc.inventory.v1.GetStockRequest;
import com.procurehub.grpc.inventory.v1.GetStockResponse;
import com.procurehub.grpc.inventory.v1.InventoryServiceGrpc;
import com.procurehub.grpc.inventory.v1.ReleaseReservationRequest;
import com.procurehub.grpc.inventory.v1.ReleaseReservationResponse;
import org.springframework.stereotype.Component;

@Component
public class InventoryGrpcClient {

    private final InventoryServiceGrpc.InventoryServiceBlockingStub stub;

    public InventoryGrpcClient(InventoryServiceGrpc.InventoryServiceBlockingStub stub) {
        this.stub = stub;
    }

    public CheckAndReserveResponse checkAndReserve(long productId, int quantity) {
        return checkAndReserve(0L, productId, quantity);
    }

    public CheckAndReserveResponse checkAndReserve(long orderId, long productId, int quantity) {
        CheckAndReserveRequest request = CheckAndReserveRequest.newBuilder()
                .setOrderId(orderId)
                .setProductId(productId)
                .setQuantity(quantity)
                .build();
        return stub.checkAndReserve(request);
    }

    public ReleaseReservationResponse releaseReservation(long productId, int quantity) {
        return releaseReservation(0L, productId, quantity);
    }

    public ReleaseReservationResponse releaseReservation(long orderId, long productId, int quantity) {
        ReleaseReservationRequest request = ReleaseReservationRequest.newBuilder()
                .setOrderId(orderId)
                .setProductId(productId)
                .setQuantity(quantity)
                .build();
        return stub.releaseReservation(request);
    }

    public GetStockResponse getStock(long productId) {
        GetStockRequest request = GetStockRequest.newBuilder()
                .setProductId(productId)
                .build();
        return stub.getStock(request);
    }
}
