package com.zimpic.backend.entity;

import java.time.Instant;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "vision_image",
    indexes = {
        @Index(name = "idx_vision_image_room_type", columnList = "room_type"),
        @Index(name = "idx_vision_image_created_at", columnList = "created_at"),
        @Index(name = "idx_vision_image_expires_at", columnList = "expires_at")
})
public class VisionImage {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ColumnDefault("''")
  @Column(name = "room_type", nullable = false, length = 30)
  private String roomType = "";

  @Column(name = "image", length = 255)
  private String image;

  @ColumnDefault("''")
  @Column(name = "image_file_name", nullable = false, length = 255)
  private String imageFileName = "";

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  public VisionImage(String roomType, String image, String imageFileName, Instant expiresAt) {
    this.roomType = roomType;
    this.image = image;
    this.imageFileName = imageFileName;
    this.expiresAt = expiresAt;
  }

  // 견적 만료 시각이 현재 이미지 만료 시각보다 늦으면
  // 보관 기간을 연장하기 위한 메서드
  public void extendExpiry(Instant candidate) {
    if(candidate.isAfter(this.expiresAt)) {
      this.expiresAt = candidate;
    }
  }
}
