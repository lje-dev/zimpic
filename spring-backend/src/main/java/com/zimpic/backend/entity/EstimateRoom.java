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
    name = "estimate_room",
    uniqueConstraints = @UniqueConstraint(name = "uq_estimate_room_estimate_sort_order", columnNames = {"estimate_id", "sort_order"}),
    indexes = @Index(name = "idx_estimate_room_image", columnList = "vision_image_id")
)
public class EstimateRoom {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "estimate_id", nullable = false)
  private Estimate estimate;
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "vision_image_id")
  private VisionImage visionImage;
  @ColumnDefault("''") @Column(name = "room_type", nullable = false, length = 30)
  private String roomType = "";
  @ColumnDefault("''") @Column(name = "image_url", nullable = false, columnDefinition = "text")
  private String imageUrl = "";
  @ColumnDefault("0") @Column(name = "sort_order", nullable = false)
  private Integer sortOrder = 0;
  @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
