package com.zimpic.backend.entity;

import com.zimpic.backend.entity.type.BodyType;
import com.zimpic.backend.entity.type.TruckType;
import jakarta.persistence.*;
import jakarta.validation.constraints.Positive;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "policy_truck_spec",
  indexes = @Index(name = "idx_truck_spec_active", columnList = "is_active")
)
public class TruckSpec {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(name = "truck_type", nullable = false, unique = true, length = 10)
  private TruckType truckType;

  @Column(name = "name", nullable = false, length = 50)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "body_type", nullable = false, length = 10)
  private BodyType bodyType;

  @Positive
  @Column(name = "inner_width_cm", nullable = false)
  private Integer innerWidthCm;

  @Positive
  @Column(name = "inner_length_cm", nullable = false)
  private Integer innerLengthCm;

  @Positive
  @Column(name = "inner_height_cm", nullable = false)
  private Integer innerHeightCm;

  @Positive
  @Column(name = "cbm_geom", nullable = false)
  private Integer cbmGeom;

  @ColumnDefault("true")
  @Column(name = "is_active", nullable = false)
  private boolean active = true;
}
