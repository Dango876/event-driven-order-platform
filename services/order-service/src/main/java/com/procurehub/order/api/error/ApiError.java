package com.procurehub.order.api.error;

import java.time.LocalDateTime;

public record ApiError(LocalDateTime timestamp, int status, String message) {
}
