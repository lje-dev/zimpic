package com.zimpic.backend.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    // 오류마다 사용할 HTTP 상태와 기본 메시지를 한곳에서 관리

    // 4xx
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값을 확인해 주세요."),
    FURNITURE_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 가구 정보를 찾을 수 없습니다."),
    BUSINESS_RULE_VIOLATION(HttpStatus.UNPROCESSABLE_CONTENT, "처리할 수 없는 요청입니다."),
    
    // 5xx
    EXTERNAL_API_BAD_RESPONSE(HttpStatus.BAD_GATEWAY, "외부 서비스 응답을 처리할 수 없습니다."),
    EXTERNAL_API_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "외부 서비스 응답 시간이 초과되었습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서비스 내부 오류가 발생했습니다.")
    ;

    // 클라이언트에 반환할 HTTP상태와 기본 메세지
    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    // GlobalExceptionHandler에서 HTTP 응답 상태를 만들 때 사용
    public HttpStatus httpStatus() {
        return httpStatus;
    }

    // 별도 메세지가 없을 때 사용자 응답에 사용할 메세지 
    public String defaultMessString() {
        return defaultMessage;
    }
    
}
