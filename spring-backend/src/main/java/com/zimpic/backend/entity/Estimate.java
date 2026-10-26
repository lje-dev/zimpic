package com.zimpic.backend.entity;

import java.time.Instant;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;

import com.zimpic.backend.entity.type.MoveType;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "estimate", indexes = {
    @Index(name = "idx_estimate_move_type", columnList = "move_type"),
    @Index(name = "idx_estimate_created_at", columnList = "created_at")
})
public class Estimate {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(name = "move_type", nullable = false, length = 20)
  private MoveType moveType;
  @Column(name = "area", nullable = false)
  private Integer area;

  @Column(name = "origin_address", nullable = false, columnDefinition = "text")
  private String originAddress;
  @Column(name = "origin_floor", nullable = false)
  private Integer originFloor;
  @ColumnDefault("false") @Column(name = "origin_has_elevator", nullable = false)
  private boolean originHasElevator;
  @ColumnDefault("false") @Column(name = "origin_use_ladder", nullable = false)
  private boolean originUseLadder;

  @Column(name = "dest_address", columnDefinition = "text")
  private String destAddress;
  @Column(name = "dest_floor")
  private Integer destFloor;
  @ColumnDefault("false") @Column(name = "dest_has_elevator")
  private Boolean destHasElevator = false;
  @ColumnDefault("false") @Column(name = "dest_use_ladder")
  private Boolean destUseLadder = false;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
