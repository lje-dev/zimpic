package com.zimpic.backend.entity;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "estimate_price", indexes = @Index(name = "idx_estimate_price_calculated_at", columnList = "calculated_at"))
public class EstimatePrice {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "estimate_id", nullable = false, unique = true)
  private Estimate estimate;
  @Column(name = "distance_km", precision = 7, scale = 2)
  private BigDecimal distanceKm;
  @Column(name = "recommended_ton", precision = 4, scale = 1)
  private BigDecimal recommendedTon;
  @Column(name = "total_cbm", precision = 9, scale = 2)
  private BigDecimal totalCbm;
  @Column(name = "truck_capacity_cbm", precision = 9, scale = 2)
  private BigDecimal truckCapacityCbm;
  @Column(name = "load_factor_pct", precision = 5, scale = 1)
  private BigDecimal loadFactorPct;
  @Column(name = "remaining_cbm", precision = 9, scale = 2)
  private BigDecimal remainingCbm;
  @PositiveOrZero @Column(name = "special_item_count", nullable = false)
  private Integer specialItemCount = 0;
  @PositiveOrZero @Column(name = "boxes_count")
  private Integer boxesCount;
  @Column(name = "boxes_description", length = 255)
  private String boxesDescription;
  @PositiveOrZero @Column(name = "total_amount", nullable = false)
  private Integer totalAmount;
  @CreationTimestamp @Column(name = "calculated_at", nullable = false, updatable = false)
  private Instant calculatedAt;
}
