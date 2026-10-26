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
    name = "policy_ladder_fee_rule",
    uniqueConstraints = @UniqueConstraint(name = "uq_ladder_group_floor", columnNames = {"ladder_truck_group", "floor_from", "floor_to"}),
    indexes = @Index(name = "idx_ladder_active", columnList = "is_active")
)
public class LadderFeeRule {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(name = "ladder_truck_group", nullable = false, length = 10)
  private TruckType ladderTruckGroup;

  @PositiveOrZero
  @Column(name = "floor_from", nullable = false)
  private Integer floorFrom;

  @PositiveOrZero
  @Column(name = "floor_to", nullable = false)
  private Integer floorTo;

  @PositiveOrZero
  @Column(name = "base_amount", nullable = false)
  private Integer baseAmount;

  @ColumnDefault("true")
  @Column(name = "is_active", nullable = false)
  private boolean active = true;
}
