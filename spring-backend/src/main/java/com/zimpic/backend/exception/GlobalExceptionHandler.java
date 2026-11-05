package com.zimpic.backend.exception;

import jakarta.persistence.ElementCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.zimpic.backend.dto.response.ErrorResponse;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

// 모든 REST Controller에서 발생한 예외를 공통으로 처리
@RestControllerAdvice 
public class GlobalExceptionHandler {

    // 마지막 500 handler에서 실제 원인을 서버 로그로 기록할 때 사용
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Service에서 의도적으로 발생시킨 예외 (비지니스 예외) 처리
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.errorCode();
        ErrorResponse response = ErrorResponse.of(
            errorCode, 
            exception.getMessage()
        );
        return ResponseEntity.
            status(errorCode.httpStatus())
            .body(response);
    }

    // 400 오류 처리
    // DTO validation 실패, 잘못된 JSON, 필수 파라미터 누락 등
    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        HttpMessageNotReadableException.class,
        MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ErrorResponse> handleInvalidRequest(Exception exception) {
        ErrorCode errorCode = ErrorCode.INVALID_REQUEST;
        ErrorResponse response = ErrorResponse.of(
            errorCode,
            errorCode.defaultMessage()
        );
        return ResponseEntity
            .status(errorCode.httpStatus())
            .body(response);
    }

    // 404 오류 처리
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(NoResourceFoundException exception){
        ErrorCode errorCode = ErrorCode.RESOURCE_NOT_FOUND;
        ErrorResponse response = ErrorResponse.of(
            errorCode,
            errorCode.defaultMessage()
        );
        return ResponseEntity
            .status(errorCode.httpStatus())
            .body(response);
    }

    // 405
    // 지원하지 않는 GET, POST 등 요청
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException exception) {
        ErrorCode errorCode = ErrorCode.METHOD_NOT_ALLOWED;
        ErrorResponse response = ErrorResponse.of(
            errorCode,
            errorCode.defaultMessage()
        );
        return ResponseEntity
            .status(errorCode.httpStatus())
            .body(response);
    }

    // 413
    // 업로드 크기 제한 오류
    // Spring multipart 파서가 service보다 먼저 던지는 예외라 따로 처리해줌
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException exception) {
        ErrorCode errorCode = ErrorCode.IMAGE_FILE_TOO_LARGE;
        ErrorResponse response = ErrorResponse.of(
            errorCode,
            errorCode.defaultMessage()
        );
        return ResponseEntity
            .status(errorCode.httpStatus())
            .body(response);
    }

    // 500
    // 위에 없는 나머지 예외를 500 응답으로 공통 처리
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception exception) {
        log.error("예상하지 못한 예외",exception);

        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        ErrorResponse response = ErrorResponse.of(
            errorCode, 
            errorCode.defaultMessage()
        );
        return ResponseEntity
            .status(errorCode.httpStatus())
            .body(response);
    }

}
