package com.zimpic.backend.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "vision_log", indexes = {
    @Index(name = "idx_vision_log_request_id", columnList = "request_id"),
    @Index(name = "idx_vision_log_expires_at", columnList = "expires_at")
})
public class VisionLog {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "request_id", nullable = false, columnDefinition = "uuid")
  private UUID requestId;

  @Column(name = "room_type", length = 30)
  private String roomType;

  @Column(name = "file_index")
  private Integer fileIndex;

  @Column(name = "failure_stage", nullable = false, length = 30)
  private String failureStage;

  @Column(name = "error_code", nullable = false, length = 60)
  private String errorCode;

  @Column(name = "exception_type", nullable = false, length = 100)
  private String exceptionType;

  @Column(name = "error_message", nullable = false, length = 500)
  private String errorMessage;

  @Column(name = "retained_file_path", length = 500)
  private String retainedFilePath;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  public VisionLog(UUID requestId, String roomType, Integer fileIndex, String failureStage, String errorCode, String exceptionType, String errorMessage, String retainedFilePath, Instant expiresAt) {
    this.requestId = requestId;
    this.roomType = roomType;
    this.fileIndex = fileIndex;
    this.failureStage = failureStage;
    this.errorCode = errorCode;
    this.exceptionType = exceptionType;
    this.errorMessage = errorMessage;
    this.retainedFilePath = retainedFilePath;
    this.expiresAt = expiresAt;
  }

}
