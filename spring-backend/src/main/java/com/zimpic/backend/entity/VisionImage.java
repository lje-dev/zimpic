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
    indexes = {@Index(name = "idx_vision_image_room_type", columnList = "room_type"),
      @Index(name = "idx_vision_image_created_at", columnList = "created_at")}
)
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

  @ColumnDefault("0")
  @Column(name = "sort_order", nullable = false)
  private Integer sortOrder = 0;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
