package com.zimpic.backend.entity;

import com.zimpic.backend.entity.type.TruckType;
import jakarta.persistence.*;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "policy_distance_fee_rule",
    indexes = @Index(name = "idx_distance_active", columnList = "is_active")
)
public class DistanceFeeRule {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(name = "truck_type", nullable = false, unique = true, length = 10)
  private TruckType truckType;

  @Positive
  @ColumnDefault("20")
  @Column(name = "base_km", nullable = false)
  private Integer baseKm = 20;

  @Positive
  @ColumnDefault("10")
  @Column(name = "unit_km", nullable = false)
  private Integer unitKm = 10;

  @PositiveOrZero
  @Column(name = "per_unit_amount", nullable = false)
  private Integer perUnitAmount;

  @ColumnDefault("true")
  @Column(name = "is_active", nullable = false)
  private boolean active = true;

}
