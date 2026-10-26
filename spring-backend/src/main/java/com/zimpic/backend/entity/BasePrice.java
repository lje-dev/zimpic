package com.zimpic.backend.entity;

import com.zimpic.backend.entity.type.MoveType;
import com.zimpic.backend.entity.type.TruckType;
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
    name = "policy_base_price",
    uniqueConstraints = @UniqueConstraint(name = "uq_base_price_move_truck", columnNames = {"move_type", "truck_type"}),
    indexes = @Index(name = "idx_base_price_active", columnList = "is_active")
)
public class BasePrice {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(name = "move_type", nullable = false, length = 20)
  private MoveType moveType;

  @Enumerated(EnumType.STRING)
  @Column(name = "truck_type", nullable = false, length = 10)
  private TruckType truckType;

  @PositiveOrZero
  @Column(name = "base_amount", nullable = false)
  private Integer baseAmount;

  @PositiveOrZero
  @ColumnDefault("0")
  @Column(name = "included_workers", nullable = false)
  private Integer includedWorkers = 0;

  @ColumnDefault("''")
  @Column(name = "note", nullable = false, length = 200)
  private String note = "";

  @ColumnDefault("true")
  @Column(name = "is_active", nullable = false)
  private boolean active = true;
}
