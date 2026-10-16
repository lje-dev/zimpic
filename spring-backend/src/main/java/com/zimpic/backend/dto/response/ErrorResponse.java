package com.zimpic.backend.dto.response;

import com.zimpic.backend.exception.ErrorCode;

public record ErrorResponse(boolean success, String code, String message) {
    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(false, errorCode.name(), message);
    }
}
