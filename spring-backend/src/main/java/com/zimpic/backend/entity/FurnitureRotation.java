package com.zimpic.backend.entity;

import com.zimpic.backend.entity.type.RotationCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "furniture_rotation",
    uniqueConstraints = @UniqueConstraint(
            name = "uq_policy_furniture_rotation_furniture_orientation",
            columnNames = {"furniture_id", "orientation_code"}
    ),
    indexes = {
        @Index(name = "idx_furniture_rotation_furniture", columnList = "furniture_id"
        ),
        @Index(name = "idx_furniture_rotation_code", columnList = "orientation_code")
    }
)
public class FurnitureRotation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "furniture_id", nullable = false)
    private Furniture furniture;

    @Enumerated(EnumType.STRING)
    @Column(name = "orientation_code", nullable = false, length = 3)
    private RotationCode orientationCode;

    @Column(name = "note", length = 100)
    private String note;


    public FurnitureRotation(Furniture furniture, RotationCode orientationCode, String note){
       this.furniture = furniture;
       this.orientationCode = orientationCode;
       this.note = note;
    }
}
