package com.zimpic.backend.entity;

import java.math.BigDecimal;

import org.hibernate.annotations.ColumnDefault;

import com.zimpic.backend.entity.type.FurnitureCategory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter 
@NoArgsConstructor (access = AccessLevel.PROTECTED)
@Entity 
@Table (indexes = {@Index (name = "idx_furniture_category", columnList = "category")})
public class Furniture {

    @Id 
    @GeneratedValue (strategy = GenerationType.IDENTITY) // 자동증가
    private Long id;

    @NotNull 
    @Enumerated (EnumType.STRING) // Enum의 인덱스값이 아닌 상수명을 사용한다  
    @Column (name = "category", nullable = false, length = 20)  
    private FurnitureCategory category;
 
    @Column (name = "yolo_id", unique = true)
    private Integer yoloId;

    @NotBlank
    @Column (name = "name_en", nullable = false, unique = true, length = 64)
    private String nameEn;

    @NotBlank 
    @Column (name = "name_kr", nullable = false, length = 64)
    private String nameKr;

    @Column (name = "description", length = 255)
    private String description;

    @Column (name = "reference_model", length = 255)
    private String referenceModel;

    @Column (name = "reference_url", length = 500)
    private String referenceUrl;

    @Positive 
    @Column (name = "width_cm", nullable = false)
    private Integer widthCm;

    @Positive 
    @Column (name = "depth_cm", nullable = false)
    private Integer depthCm;

    @Positive 
    @Column (name = "height_cm", nullable = false)
    private Integer heightCm;

    @NotNull 
    @DecimalMin ("0.0")
    @DecimalMax ("10.0")
    @ColumnDefault ("1.0")
    @Column (name = "padding_cm", nullable = false, precision = 4, scale = 1)  // DECIMAL(4,1): 전체 네 자리 중 소수점 아래 한 자리를 사용
    private BigDecimal paddingCm;

    @ColumnDefault ("false")
    @Column (name = "needs_disassembly", nullable = false)
    private boolean needsDisassembly;

    @ColumnDefault ("false")
    @Column (name = "stackable", nullable = false)
    private boolean stackable;

    @ColumnDefault ("false")
    @Column (name = "can_stack_on_top", nullable = false)
    private boolean canStackOnTop;

    public Furniture(
        FurnitureCategory category,
        Integer yoloId,
        String nameEn,
        String nameKr,
        String description,
        String referenceModel,
        String referenceUrl,
        Integer widthCm,
        Integer depthCm,
        Integer heightCm,
        BigDecimal paddingCm,
        boolean needsDisassembly,
        boolean stackable,
        boolean canStackOnTop
    ){
        this.category = category;
        this.yoloId = yoloId;
        this.nameEn = nameEn;
        this.nameKr = nameKr;
        this.description = description;
        this.referenceModel = referenceModel;
        this.referenceUrl = referenceUrl;
        this.widthCm = widthCm;
        this.depthCm = depthCm;
        this.heightCm = heightCm;
        this.paddingCm = paddingCm;
        this.needsDisassembly = needsDisassembly;
        this.stackable = stackable;
        this.canStackOnTop = canStackOnTop;
    }
}
