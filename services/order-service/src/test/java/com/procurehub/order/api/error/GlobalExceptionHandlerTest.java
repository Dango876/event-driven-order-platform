package com.procurehub.order.api.error;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldMapGrpcNotFoundTo404() {
        StatusRuntimeException ex = new StatusRuntimeException(Status.NOT_FOUND.withDescription("order not found"));
        ResponseEntity<ApiError> response = handler.handleGrpc(ex);

        assertEquals(404, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().status());
        assertEquals("order not found", response.getBody().message());
    }

    @Test
    void shouldMapGrpcInvalidArgumentTo400() {
        StatusRuntimeException ex = new StatusRuntimeException(Status.INVALID_ARGUMENT.withDescription("bad request"));
        ResponseEntity<ApiError> response = handler.handleGrpc(ex);

        assertEquals(400, response.getStatusCode().value());
        assertEquals(400, response.getBody().status());
    }

    @Test
    void shouldMapUnknownGrpcStatusTo502WithDefaultMessage() {
        StatusRuntimeException ex = new StatusRuntimeException(Status.UNAVAILABLE);
        ResponseEntity<ApiError> response = handler.handleGrpc(ex);

        assertEquals(502, response.getStatusCode().value());
        assertEquals("gRPC call failed", response.getBody().message());
    }

    @Test
    void shouldMapIllegalArgumentTo400() {
        ResponseEntity<ApiError> response = handler.handleBadRequest(new IllegalArgumentException("bad data"));
        assertEquals(400, response.getStatusCode().value());
        assertEquals("bad data", response.getBody().message());
    }

    @Test
    void shouldMapIllegalStateTo409() {
        ResponseEntity<ApiError> response = handler.handleConflict(new IllegalStateException("conflict"));
        assertEquals(409, response.getStatusCode().value());
        assertEquals("conflict", response.getBody().message());
    }

    @Test
    void shouldMapAnyExceptionTo500() {
        ResponseEntity<ApiError> response = handler.handleAny(new RuntimeException("boom"));
        assertEquals(500, response.getStatusCode().value());
        assertEquals("Internal server error", response.getBody().message());
    }
}

