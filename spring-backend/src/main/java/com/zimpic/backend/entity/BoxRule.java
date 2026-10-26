package com.zimpic.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "policy_box_rule")
public class BoxRule {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Positive
  @Column(name = "area_min_py", nullable = false)
  private Integer areaMinPy;

  @Positive
  @Column(name = "area_max_py", nullable = false)
  private Integer areaMaxPy;

  @PositiveOrZero
  @Column(name = "boxes_min", nullable = false)
  private Integer boxesMin;

  @PositiveOrZero
  @Column(name = "boxes_max", nullable = false)
  private Integer boxesMax;

  @PositiveOrZero
  @Column(name = "boxes_avg", nullable = false)
  private Integer boxesAvg;

  @Column(name = "note", nullable = false, length = 100)
  private String note;

  @ColumnDefault("true")
  @Column(name = "is_active", nullable = false)
  private boolean active = true;

  @AssertTrue(message = "평수와 박스 범위가 올바르지 않습니다.")
  public boolean isValidRange(){
    if(areaMinPy == null || areaMaxPy == null || boxesMin == null || boxesMax == null || boxesAvg == null) {
      return true;
    }
    return areaMinPy <= areaMaxPy && boxesMin <= boxesAvg && boxesAvg <= boxesMax;
  }
}
