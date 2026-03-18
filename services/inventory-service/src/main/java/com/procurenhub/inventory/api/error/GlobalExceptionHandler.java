package com.procurehub.inventory.api.error;

import com.procurehub.inventory.service.NotEnoughStockException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotEnoughStockException.class)
    public ResponseEntity<ApiError> handleNotEnough(NotEnoughStockException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError(LocalDateTime.now(), 409, ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError(LocalDateTime.now(), 400, ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleOther(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError(LocalDateTime.now(), 500, "Internal server error"));
    }
}
