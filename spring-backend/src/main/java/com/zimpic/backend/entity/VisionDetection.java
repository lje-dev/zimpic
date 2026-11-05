package com.zimpic.backend.entity;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "vision_detection", indexes = {
    @Index(name = "idx_detection_image", columnList = "vision_image_id"),
    @Index(name = "idx_detection_furniture", columnList = "furniture_id"),
    @Index(name = "idx_detection_yolo_class", columnList = "yolo_class"),
    @Index(name = "idx_detection_created_at", columnList = "created_at")
})
public class VisionDetection {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "vision_image_id", nullable = false)
  private VisionImage visionImage;

  @Column(name = "yolo_id")
  private Integer yoloId;
  @Column(name = "yolo_class", length = 50)
  private String yoloClass;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "furniture_id")
  private Furniture furniture;

  @Column(name = "confidence", precision = 6, scale = 5)
  private BigDecimal confidence;
  @Column(name = "bbox_x", precision = 8, scale = 6)
  private BigDecimal bboxX;
  @Column(name = "bbox_y", precision = 8, scale = 6)
  private BigDecimal bboxY;
  @Column(name = "bbox_w", precision = 8, scale = 6)
  private BigDecimal bboxW;
  @Column(name = "bbox_h", precision = 8, scale = 6)
  private BigDecimal bboxH;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public VisionDetection(
      VisionImage visionImage,
      Integer yoloId,
      String yoloClass,
      Furniture furniture,
      BigDecimal confidence,
      BigDecimal bboxX, BigDecimal bboxY,
      BigDecimal bboxW, BigDecimal bboxH
  ) {
    this.visionImage = visionImage;
    this.yoloId = yoloId;
    this.yoloClass = yoloClass;
    this.furniture = furniture;
    this.confidence = confidence;
    this.bboxX = bboxX;
    this.bboxY = bboxY;
    this.bboxW = bboxW;
    this.bboxH = bboxH;
  }
}
