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
    name = "policy_special_item_fee",
    indexes = @Index(name = "idx_special_fee_active", columnList = "is_active")
)
public class SpecialItemFee {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "furniture_id", nullable = false, unique = true)
  private Furniture furniture;

  @Column(name = "description", nullable = false, length = 255)
  private String description;

  @PositiveOrZero
  @Column(name = "unit_amount", nullable = false)
  private Integer unitAmount;

  @ColumnDefault("true")
  @Column(name = "is_active", nullable = false)
  private boolean active = true;

}
