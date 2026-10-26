package com.zimpic.backend.entity;

import java.math.BigDecimal;

import com.zimpic.backend.entity.type.TruckType;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "estimate_truck_plan", indexes = {
    @Index(name = "idx_truck_plan_estimate", columnList = "estimate_id"),
    @Index(name = "idx_truck_plan_type", columnList = "truck_type")
})
public class EstimateTruckPlan {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "estimate_id", nullable = false)
  private Estimate estimate;
  @Enumerated(EnumType.STRING)
  @Column(name = "truck_type", nullable = false, length = 20)
  private TruckType truckType;
  @Column(name = "truck_count", nullable = false)
  private Integer truckCount = 1;
  @Column(name = "inner_w_cm", nullable = false, precision = 7, scale = 2)
  private BigDecimal innerWCm;
  @Column(name = "inner_d_cm", nullable = false, precision = 7, scale = 2)
  private BigDecimal innerDCm;
  @Column(name = "inner_h_cm", nullable = false, precision = 7, scale = 2)
  private BigDecimal innerHCm;
  @Column(name = "capacity_cbm", precision = 9, scale = 2)
  private BigDecimal capacityCbm;
  @Column(name = "load_cbm", precision = 9, scale = 2)
  private BigDecimal loadCbm;
  @Column(name = "load_factor_pct", precision = 5, scale = 1)
  private BigDecimal loadFactorPct;
  @Column(name = "remaining_cbm", precision = 9, scale = 2)
  private BigDecimal remainingCbm;
}
