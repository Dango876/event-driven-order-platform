package com.procurehub.order.client;

import com.procurehub.grpc.inventory.v1.CheckAndReserveRequest;
import com.procurehub.grpc.inventory.v1.CheckAndReserveResponse;
import com.procurehub.grpc.inventory.v1.GetStockRequest;
import com.procurehub.grpc.inventory.v1.GetStockResponse;
import com.procurehub.grpc.inventory.v1.InventoryServiceGrpc;
import com.procurehub.grpc.inventory.v1.ReleaseReservationRequest;
import com.procurehub.grpc.inventory.v1.ReleaseReservationResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InventoryGrpcClientTest {

    private Server server;
    private ManagedChannel channel;
    private TestInventoryService grpcService;
    private InventoryGrpcClient client;

    @BeforeEach
    void setUp() throws IOException {
        grpcService = new TestInventoryService();
        server = ServerBuilder.forPort(0).addService(grpcService).build().start();
        channel = ManagedChannelBuilder.forAddress("localhost", server.getPort())
                .usePlaintext()
                .build();
        client = new InventoryGrpcClient(InventoryServiceGrpc.newBlockingStub(channel));
    }

    @AfterEach
    void tearDown() {
        if (channel != null) {
            channel.shutdownNow();
        }
        if (server != null) {
            server.shutdownNow();
        }
    }

    @Test
    void checkAndReserveWithTwoArgsShouldSendOrderIdZero() {
        CheckAndReserveResponse response = client.checkAndReserve(2001L, 2);

        assertEquals(0L, grpcService.lastReserveRequest.get().getOrderId());
        assertEquals(2001L, grpcService.lastReserveRequest.get().getProductId());
        assertEquals(2, grpcService.lastReserveRequest.get().getQuantity());
        assertEquals(true, response.getSuccess());
    }

    @Test
    void checkAndReserveWithThreeArgsShouldSendProvidedOrderId() {
        CheckAndReserveResponse response = client.checkAndReserve(10L, 2001L, 3);

        assertEquals(10L, grpcService.lastReserveRequest.get().getOrderId());
        assertEquals(10L, response.getOrderId());
        assertEquals(3, response.getReservedQuantity());
    }

    @Test
    void releaseReservationWithTwoArgsShouldSendOrderIdZero() {
        ReleaseReservationResponse response = client.releaseReservation(2001L, 1);

        assertEquals(0L, grpcService.lastReleaseRequest.get().getOrderId());
        assertEquals(2001L, grpcService.lastReleaseRequest.get().getProductId());
        assertEquals(1, response.getReservedQuantity());
    }

    @Test
    void releaseReservationWithThreeArgsShouldSendProvidedOrderId() {
        ReleaseReservationResponse response = client.releaseReservation(12L, 2001L, 2);

        assertEquals(12L, grpcService.lastReleaseRequest.get().getOrderId());
        assertEquals(12L, response.getOrderId());
    }

    @Test
    void getStockShouldMapRequestAndResponse() {
        GetStockResponse response = client.getStock(500L);

        assertEquals(500L, grpcService.lastStockRequest.get().getProductId());
        assertEquals(11, response.getAvailableQuantity());
        assertEquals(4, response.getFreeQuantity());
    }

    private static final class TestInventoryService extends InventoryServiceGrpc.InventoryServiceImplBase {
        private final AtomicReference<CheckAndReserveRequest> lastReserveRequest = new AtomicReference<>();
        private final AtomicReference<ReleaseReservationRequest> lastReleaseRequest = new AtomicReference<>();
        private final AtomicReference<GetStockRequest> lastStockRequest = new AtomicReference<>();

        @Override
        public void checkAndReserve(CheckAndReserveRequest request, StreamObserver<CheckAndReserveResponse> responseObserver) {
            lastReserveRequest.set(request);
            responseObserver.onNext(CheckAndReserveResponse.newBuilder()
                    .setSuccess(true)
                    .setOrderId(request.getOrderId())
                    .setProductId(request.getProductId())
                    .setReservedQuantity(request.getQuantity())
                    .setAvailableQuantity(10)
                    .setMessage("reserved")
                    .build());
            responseObserver.onCompleted();
        }

        @Override
        public void releaseReservation(ReleaseReservationRequest request, StreamObserver<ReleaseReservationResponse> responseObserver) {
            lastReleaseRequest.set(request);
            responseObserver.onNext(ReleaseReservationResponse.newBuilder()
                    .setSuccess(true)
                    .setOrderId(request.getOrderId())
                    .setProductId(request.getProductId())
                    .setReservedQuantity(request.getQuantity())
                    .setAvailableQuantity(12)
                    .setMessage("released")
                    .build());
            responseObserver.onCompleted();
        }

        @Override
        public void getStock(GetStockRequest request, StreamObserver<GetStockResponse> responseObserver) {
            lastStockRequest.set(request);
            responseObserver.onNext(GetStockResponse.newBuilder()
                    .setProductId(request.getProductId())
                    .setAvailableQuantity(11)
                    .setReservedQuantity(7)
                    .setFreeQuantity(4)
                    .build());
            responseObserver.onCompleted();
        }
    }
}

