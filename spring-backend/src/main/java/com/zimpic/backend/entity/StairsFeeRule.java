package com.zimpic.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "policy_stairs_fee_rule",
    uniqueConstraints = @UniqueConstraint(name = "uq_stairs_floor", columnNames = {"floor_from", "floor_to"}),
    indexes = @Index(name = "idx_stairs_active", columnList = "is_active")
)
public class StairsFeeRule {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @PositiveOrZero
  @Column(name = "floor_from", nullable = false)
  private Integer floorFrom;

  @PositiveOrZero
  @Column(name = "floor_to", nullable = false)
  private Integer floorTo;

  @PositiveOrZero
  @Column(name = "per_floor_amount", nullable = false)
  private Integer perFloorAmount;

  @ColumnDefault("true")
  @Column(name = "is_active", nullable = false)
  private boolean active = true;

}
