package com.zimpic.backend.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "estimate_item", indexes = {
    @Index(name = "idx_estimate_item_room", columnList = "estimate_room_id"),
    @Index(name = "idx_estimate_item_furniture", columnList = "furniture_id")
})
public class EstimateItem {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "estimate_room_id", nullable = false)
  private EstimateRoom estimateRoom;

  @Column(name = "item_id", nullable = false, unique = true, updatable = false)
  private UUID itemId = UUID.randomUUID();

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "furniture_id")
  private Furniture furniture;

  @Column(name = "w_cm", nullable = false, precision = 7, scale = 2)
  private BigDecimal wCm;
  @Column(name = "d_cm", nullable = false, precision = 7, scale = 2)
  private BigDecimal dCm;
  @Column(name = "h_cm", nullable = false, precision = 7, scale = 2)
  private BigDecimal hCm;
  @Column(name = "padding_cm", nullable = false, precision = 4, scale = 1)
  private BigDecimal paddingCm;
  @Column(name = "packed_w_cm", nullable = false, precision = 7, scale = 2)
  private BigDecimal packedWCm;
  @Column(name = "packed_d_cm", nullable = false, precision = 7, scale = 2)
  private BigDecimal packedDCm;
  @Column(name = "packed_h_cm", nullable = false, precision = 7, scale = 2)
  private BigDecimal packedHCm;

  @Column(name = "stackable", nullable = false)
  private boolean stackable;
  @Column(name = "can_stack_on_top", nullable = false)
  private boolean canStackOnTop;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "allowed_rotations_json", nullable = false, columnDefinition = "jsonb")
  private List<String> allowedRotations = new ArrayList<>();

  @Column(name = "needs_disassembly", nullable = false)
  private boolean needsDisassembly;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
