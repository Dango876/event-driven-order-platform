package com.procurehub.order.model;

public enum OrderStatus {
    NEW,
    RESERVATION_PENDING,
    RESERVED,
    RESERVATION_FAILED,
    PAID,
    SHIPPED,
    COMPLETED,
    CANCELLED
}
