package com.zimpic.backend.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.zimpic.backend.dto.response.ErrorResponse;

@RestControllerAdvice 
public class GlobalExceptionHandler {
    // Controller 처리 중 발생한 예외를 한곳에서 받아 HTTP 상태와 ErrorResponse로 변환

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.errorCode();
        ErrorResponse response = ErrorResponse.of(
            errorCode, 
            exception.getMessage()
        );

        return ResponseEntity.status(errorCode.httpStatus()).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception exception) {
        log.error("예상하지 못한 예외",exception);

        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        ErrorResponse response = ErrorResponse.of(
            errorCode, 
            errorCode.defaultMessString()
        );
        
        return ResponseEntity.status(errorCode.httpStatus()).body(response);
    }

}
