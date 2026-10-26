package com.zimpic.backend.entity;

import com.zimpic.backend.entity.type.PriceLocation;

import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "estimate_price_detail", indexes = {
    @Index(name = "idx_price_detail_section", columnList = "estimate_price_section_id"),
    @Index(name = "idx_price_detail_scope", columnList = "scope"),
    @Index(name = "idx_price_detail_furniture", columnList = "furniture_id")
})
public class EstimatePriceDetail {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "estimate_price_section_id", nullable = false)
  private EstimatePriceSection estimatePriceSection;
  @Enumerated(EnumType.STRING)
  @Column(name = "scope", length = 10)
  private PriceLocation scope;
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "furniture_id")
  private Furniture furniture;
  @Column(name = "name_kr", length = 64)
  private String nameKr;
  @PositiveOrZero @Column(name = "amount", nullable = false)
  private Integer amount;
  @Column(name = "description", length = 255)
  private String description;
}
