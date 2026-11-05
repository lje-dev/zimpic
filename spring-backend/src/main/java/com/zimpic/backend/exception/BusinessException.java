package com.zimpic.backend.exception;

public class BusinessException extends RuntimeException {
    // 애플리케이션이 의도적으로 사용자에게 알려야 하는 오류를 표현

    private final ErrorCode errorCode;

    // 별도 메세지가 없을 경우 기본 메세지 사용
    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage());
    }

    // 상황에 맞는 메세지를 직접 전달할 때 사용
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    // 원본 예외를 보존하며 에러 메세지를 전달
    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.defaultMessage(), cause);
        this.errorCode = errorCode;
    }
    
    // GlobalExceptionHandler가 오류 코드를 조회할 때 사용
    public ErrorCode errorCode() {
        return errorCode;
    }
    
}
