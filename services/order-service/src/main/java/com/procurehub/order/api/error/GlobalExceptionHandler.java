package com.procurehub.order.api.error;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(StatusRuntimeException.class)
    public ResponseEntity<ApiError> handleGrpc(StatusRuntimeException ex) {
        Status.Code code = ex.getStatus().getCode();
        int httpStatus = switch (code) {
            case NOT_FOUND -> 404;
            case INVALID_ARGUMENT -> 400;
            default -> 502;
        };

        String message = ex.getStatus().getDescription() != null
                ? ex.getStatus().getDescription()
                : "gRPC call failed";

        return ResponseEntity.status(httpStatus)
                .body(new ApiError(LocalDateTime.now(), httpStatus, message));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(400)
                .body(new ApiError(LocalDateTime.now(), 400, ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleConflict(IllegalStateException ex) {
        return ResponseEntity.status(409)
                .body(new ApiError(LocalDateTime.now(), 409, ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleAny(Exception ex) {
        return ResponseEntity.status(500)
                .body(new ApiError(LocalDateTime.now(), 500, "Internal server error"));
    }
}
