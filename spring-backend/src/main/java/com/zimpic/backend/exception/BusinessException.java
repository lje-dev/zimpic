package com.zimpic.backend.exception;

public class BusinessException extends RuntimeException {
    // 애플리케이션이 의도적으로 사용자에게 알려야 하는 오류를 표현

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessString());
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
    
}
