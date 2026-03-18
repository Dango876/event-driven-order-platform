package com.procurehub.inventory.grpc;

import com.procurehub.grpc.inventory.v1.CheckAndReserveRequest;
import com.procurehub.grpc.inventory.v1.CheckAndReserveResponse;
import com.procurehub.grpc.inventory.v1.GetStockRequest;
import com.procurehub.grpc.inventory.v1.GetStockResponse;
import com.procurehub.grpc.inventory.v1.InventoryServiceGrpc;
import com.procurehub.grpc.inventory.v1.ReleaseReservationRequest;
import com.procurehub.grpc.inventory.v1.ReleaseReservationResponse;
import com.procurehub.inventory.api.dto.InventoryItemResponse;
import com.procurehub.inventory.event.InventoryEventPublisher;
import com.procurehub.inventory.service.InventoryService;
import com.procurehub.inventory.service.NotEnoughStockException;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Component;

@Component
public class InventoryGrpcService extends InventoryServiceGrpc.InventoryServiceImplBase {

    private final InventoryService inventoryService;
    private final InventoryEventPublisher inventoryEventPublisher;

    public InventoryGrpcService(
            InventoryService inventoryService,
            InventoryEventPublisher inventoryEventPublisher
    ) {
        this.inventoryService = inventoryService;
        this.inventoryEventPublisher = inventoryEventPublisher;
    }

    @Override
    public void checkAndReserve(CheckAndReserveRequest request, StreamObserver<CheckAndReserveResponse> responseObserver) {
        try {
            InventoryItemResponse item = inventoryService.reserve(request.getProductId(), request.getQuantity());

            CheckAndReserveResponse response = CheckAndReserveResponse.newBuilder()
                    .setSuccess(true)
                    .setProductId(item.getProductId())
                    .setAvailableQuantity(item.getAvailableQuantity())
                    .setReservedQuantity(item.getReservedQuantity())
                    .setMessage("reserved")
                    .setOrderId(request.getOrderId())
                    .build();

            inventoryEventPublisher.publishInventoryReserved(
                    request.getOrderId(),
                    item.getProductId(),
                    request.getQuantity(),
                    item.getAvailableQuantity(),
                    item.getReservedQuantity(),
                    "reserved"
            );

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (NotEnoughStockException ex) {
            CheckAndReserveResponse response = CheckAndReserveResponse.newBuilder()
                    .setSuccess(false)
                    .setProductId(request.getProductId())
                    .setMessage(ex.getMessage())
                    .setOrderId(request.getOrderId())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (IllegalArgumentException ex) {
            responseObserver.onError(Status.NOT_FOUND.withDescription(ex.getMessage()).asRuntimeException());
        } catch (Exception ex) {
            responseObserver.onError(Status.INTERNAL.withDescription("Internal server error").asRuntimeException());
        }
    }

    @Override
    public void releaseReservation(ReleaseReservationRequest request, StreamObserver<ReleaseReservationResponse> responseObserver) {
        try {
            InventoryItemResponse item = inventoryService.release(request.getProductId(), request.getQuantity());

            ReleaseReservationResponse response = ReleaseReservationResponse.newBuilder()
                    .setSuccess(true)
                    .setProductId(item.getProductId())
                    .setAvailableQuantity(item.getAvailableQuantity())
                    .setReservedQuantity(item.getReservedQuantity())
                    .setMessage("released")
                    .setOrderId(request.getOrderId())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (IllegalArgumentException ex) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(ex.getMessage()).asRuntimeException());
        } catch (Exception ex) {
            responseObserver.onError(Status.INTERNAL.withDescription("Internal server error").asRuntimeException());
        }
    }

    @Override
    public void getStock(GetStockRequest request, StreamObserver<GetStockResponse> responseObserver) {
        try {
            InventoryItemResponse item = inventoryService.getByProductId(request.getProductId());

            GetStockResponse response = GetStockResponse.newBuilder()
                    .setProductId(item.getProductId())
                    .setAvailableQuantity(item.getAvailableQuantity())
                    .setReservedQuantity(item.getReservedQuantity())
                    .setFreeQuantity(item.getFreeQuantity())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (IllegalArgumentException ex) {
            responseObserver.onError(Status.NOT_FOUND.withDescription(ex.getMessage()).asRuntimeException());
        } catch (Exception ex) {
            responseObserver.onError(Status.INTERNAL.withDescription("Internal server error").asRuntimeException());
        }
    }
}
