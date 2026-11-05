package com.zimpic.backend.service;

import com.zimpic.backend.entity.VisionLog;
import com.zimpic.backend.exception.BusinessException;
import com.zimpic.backend.exception.ErrorCode;
import com.zimpic.backend.repository.VisionLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
public class VisionLogService {

  private final VisionLogRepository repository;
  private final Duration failureRetention;

  public VisionLogService(
      VisionLogRepository repository,
      @Value("${vision.failure-retention}") Duration failureRetention
  ) {
    this.repository = repository;
    this.failureRetention = failureRetention;
  }

  // REQUIRES_NEW: 이 메서드를 호출할 때바다 별도의 DB 트랜잭션을 시작하라는 의미
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordFailure(UUID requestId, String roomType, Integer fileIndex, String stage, RuntimeException exception, String retainedFilePath) {
    ErrorCode code = exception instanceof BusinessException business
        ? business.errorCode() : ErrorCode.INTERNAL_SERVER_ERROR;

    String message = exception instanceof BusinessException business
        ? business.getMessage() : code.defaultMessage();

    if (message == null) message = code.defaultMessage();
    message = message.replaceAll("[\\r\\n\\t]", "");

    if (message.length() > 500) message = message.substring(0, 500);

    repository.save(
        new VisionLog(requestId, roomType, fileIndex, stage, code.name(),
            exception.getClass().getSimpleName(), message, retainedFilePath,
            Instant.now().plus(failureRetention))
    );
    log.error("Vision 처리 실패: requestId={}, stage={}, fileIndex={}, code={}", requestId, stage, fileIndex, code.name(), exception);
  }
}
