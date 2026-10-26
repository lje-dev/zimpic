package com.zimpic.backend.entity;

import com.zimpic.backend.entity.type.PriceType;

import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "estimate_price_section", indexes = {
    @Index(name = "idx_price_section_price", columnList = "estimate_price_id"),
    @Index(name = "idx_price_section_key", columnList = "key")
})
public class EstimatePriceSection {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "estimate_price_id", nullable = false)
  private EstimatePrice estimatePrice;
  @Enumerated(EnumType.STRING)
  @Column(name = "key", nullable = false, length = 20)
  private PriceType key;
  @Column(name = "title", nullable = false, length = 50)
  private String title;
  @PositiveOrZero @Column(name = "amount", nullable = false)
  private Integer amount;
  @Column(name = "description", length = 255)
  private String description;
}
