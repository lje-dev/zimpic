# 04-1. JPA Entity 구현

이 문서는 [04. Spring Boot 백엔드 준비 및 단계별 구현](04-spring-boot.md)의 11번을 실제로 입력하기 위한 상세 가이드다. 10번에서 구현한 `Furniture`를 제외하고 정책 8개, 이미지·탐지 2개, 견적 7개로 총 17개 Entity를 작성한다.

기준이 다르면 다음 우선순위를 사용한다.

```text
docs/07-db-schema.md → 현재 CSV 데이터 → backend/*/models.py
```

## 1. 구현 원칙

- 모든 Entity는 `@Getter`, `@NoArgsConstructor(access = AccessLevel.PROTECTED)`를 사용하고 `@Setter`와 `@Data`는 사용하지 않는다.
- 기본 키는 `Long`, 자동 생성 방식은 `GenerationType.IDENTITY`를 사용한다.
- 금액은 `Integer`, 소수 자릿수가 있는 치수·부피·비율은 `BigDecimal`을 사용한다.
- 날짜는 PostgreSQL `TIMESTAMP WITH TIME ZONE`과 대응하기 위해 `Instant`를 사용하고 `@CreationTimestamp`로 생성한다.
- 외래키는 필요한 자식→부모 단방향 관계만 만든다. 부모 Entity에 `List<자식>`을 추가하지 않는다.
- 조회 중 의도하지 않은 SQL을 줄이기 위해 `@ManyToOne`과 `@OneToOne`은 `fetch = FetchType.LAZY`로 지정한다.
- Django의 `CASCADE`는 부모 삭제 시 자식도 삭제되어야 한다는 정책이고, `SET_NULL`은 부모 삭제 후 외래키를 `NULL`로 남기는 정책이다. 최종 DB 삭제 규칙은 Flyway 전환 시 FK DDL로 확정한다.
- `@ColumnDefault`는 새 테이블 DDL의 기본값이다. Java로 객체를 만들 때 필요한 값은 생성자에서 명시적으로 전달한다.
- `@ColumnDefault`만으로 Java 필드값이 설정되지는 않는다. 정책 Entity의 `active`는 Java에서도 `true`로 초기화해 JPA 저장 시 기본 활성 상태를 보장한다.

## 2. 공통 Enum

먼저 `entity/type`의 Enum을 완성한다. 모든 Enum은 `@Enumerated(EnumType.STRING)`으로 Java 상수 이름을 신규 DB에 그대로 저장한다.

### MoveType.java

```java
package com.zimpic.backend.entity.type;

public enum MoveType {
    GENERAL,
    PACKING
}
```

### BodyType.java

```java
package com.zimpic.backend.entity.type;

public enum BodyType {
    CARGO,
    TOP
}
```

### RotationCode.java

```java
package com.zimpic.backend.entity.type;

public enum RotationCode {
    WHD,
    WDH,
    HWD,
    HDW,
    DWH,
    DHW
}
```

### PriceType.java

`EstimatePriceSection.key`에 사용한다.

```java
package com.zimpic.backend.entity.type;

public enum PriceType {
    BASE,
    LADDER,
    STAIRS,
    DISTANCE,
    SPECIAL
}
```

### PriceLocation.java

`EstimatePriceDetail.scope`에 사용한다.

```java
package com.zimpic.backend.entity.type;

public enum PriceLocation {
    ORIGIN,
    DEST
}
```

### TruckType.java

기존 Django는 트럭 타입을 `1T`, `2_5T`, `5T` 형식으로 사용한다. Java Enum 상수는 숫자로 시작할 수 없으므로 Spring Boot와 신규 DB에서는 `T1`, `T2_5`, `T5` 형식으로 변경한다. 프론트에서 요청하거나 응답받아야 하는 값이 다르면 이후 API 구현 단계에서 DTO가 변환하도록 한다.

```java
package com.zimpic.backend.entity.type;

public enum TruckType {
    T1,
    T2_5,
    T5,
    T6,
    T7_5,
    T10
}
```

## 3. 정책 Entity 8개

### 3.1 FurnitureRotation

`Furniture`가 삭제되면 연결된 회전 정보도 함께 삭제한다. `(furniture_id, orientation_code)`는 중복될 수 없다.

```java
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
        @Index(name = "idx_furniture_rotation_furniture", columnList = "furniture_id"),
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

    public FurnitureRotation(Furniture furniture, RotationCode orientationCode, String note) {
        this.furniture = furniture;
        this.orientationCode = orientationCode;
        this.note = note;
    }
}
```

### 3.2 BoxRule

평수 구간 겹침은 단일 행 제약조건으로 표현할 수 없으므로 이후 Service에서 조회하여 검증한다. Entity에는 한 행 내부의 범위만 검증한다.

```java
package com.zimpic.backend.entity;

import org.hibernate.annotations.ColumnDefault;

import jakarta.persistence.*;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "policy_box_rule")
public class BoxRule {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Positive @Column(name = "area_min_py", nullable = false)
    private Integer areaMinPy;
    @Positive @Column(name = "area_max_py", nullable = false)
    private Integer areaMaxPy;
    @PositiveOrZero @Column(name = "boxes_min", nullable = false)
    private Integer boxesMin;
    @PositiveOrZero @Column(name = "boxes_max", nullable = false)
    private Integer boxesMax;
    @PositiveOrZero @Column(name = "boxes_avg", nullable = false)
    private Integer boxesAvg;
    @Column(name = "note", nullable = false, length = 100)
    private String note;
    @ColumnDefault("true") @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @AssertTrue(message = "평수와 박스 범위가 올바르지 않습니다.")
    public boolean isValidRange() {
        if (areaMinPy == null || areaMaxPy == null || boxesMin == null || boxesAvg == null || boxesMax == null) {
            return true;
        }
        return areaMinPy <= areaMaxPy && boxesMin <= boxesAvg && boxesAvg <= boxesMax;
    }
}
```

### 3.3 TruckSpec

```java
package com.zimpic.backend.entity;

import org.hibernate.annotations.ColumnDefault;

import com.zimpic.backend.entity.type.BodyType;
import com.zimpic.backend.entity.type.TruckType;

import jakarta.persistence.*;
import jakarta.validation.constraints.Positive;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "policy_truck_spec", indexes = @Index(name = "idx_truck_spec_active", columnList = "is_active"))
public class TruckSpec {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    @Column(name = "truck_type", nullable = false, unique = true, length = 10)
    private TruckType truckType;
    @Column(name = "name", nullable = false, length = 50)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(name = "body_type", nullable = false, length = 10)
    private BodyType bodyType;
    @Positive @Column(name = "inner_width_cm", nullable = false)
    private Integer innerWidthCm;
    @Positive @Column(name = "inner_length_cm", nullable = false)
    private Integer innerLengthCm;
    @Positive @Column(name = "inner_height_cm", nullable = false)
    private Integer innerHeightCm;
    @Positive @Column(name = "cbm_geom", nullable = false)
    private Integer cbmGeom;
    @ColumnDefault("true") @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
```

### 3.4 BasePrice

```java
package com.zimpic.backend.entity;

import org.hibernate.annotations.ColumnDefault;

import com.zimpic.backend.entity.type.MoveType;
import com.zimpic.backend.entity.type.TruckType;

import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "policy_base_price",
    uniqueConstraints = @UniqueConstraint(name = "uq_base_price_move_truck", columnNames = {"move_type", "truck_type"}),
    indexes = @Index(name = "idx_base_price_active", columnList = "is_active")
)
public class BasePrice {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    @Column(name = "move_type", nullable = false, length = 20)
    private MoveType moveType;
    @Enumerated(EnumType.STRING)
    @Column(name = "truck_type", nullable = false, length = 10)
    private TruckType truckType;
    @PositiveOrZero @Column(name = "base_amount", nullable = false)
    private Integer baseAmount;
    @PositiveOrZero @ColumnDefault("0") @Column(name = "included_workers", nullable = false)
    private Integer includedWorkers = 0;
    @ColumnDefault("''") @Column(name = "note", nullable = false, length = 200)
    private String note = "";
    @ColumnDefault("true") @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
```

### 3.5 LadderFeeRule

```java
package com.zimpic.backend.entity;

import org.hibernate.annotations.ColumnDefault;

import com.zimpic.backend.entity.type.TruckType;

import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "policy_ladder_fee_rule",
    uniqueConstraints = @UniqueConstraint(name = "uq_ladder_group_floor", columnNames = {"ladder_truck_group", "floor_from", "floor_to"}),
    indexes = @Index(name = "idx_ladder_active", columnList = "is_active")
)
public class LadderFeeRule {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    @Column(name = "ladder_truck_group", nullable = false, length = 10)
    private TruckType ladderTruckGroup;
    @PositiveOrZero @Column(name = "floor_from", nullable = false)
    private Integer floorFrom;
    @PositiveOrZero @Column(name = "floor_to", nullable = false)
    private Integer floorTo;
    @PositiveOrZero @Column(name = "base_amount", nullable = false)
    private Integer baseAmount;
    @ColumnDefault("true") @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
```

### 3.6 StairsFeeRule

```java
package com.zimpic.backend.entity;

import org.hibernate.annotations.ColumnDefault;

import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "policy_stairs_fee_rule",
    uniqueConstraints = @UniqueConstraint(name = "uq_stairs_floor", columnNames = {"floor_from", "floor_to"}),
    indexes = @Index(name = "idx_stairs_active", columnList = "is_active")
)
public class StairsFeeRule {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @PositiveOrZero @Column(name = "floor_from", nullable = false)
    private Integer floorFrom;
    @PositiveOrZero @Column(name = "floor_to", nullable = false)
    private Integer floorTo;
    @PositiveOrZero @Column(name = "per_floor_amount", nullable = false)
    private Integer perFloorAmount;
    @ColumnDefault("true") @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
```

### 3.7 DistanceFeeRule

```java
package com.zimpic.backend.entity;

import org.hibernate.annotations.ColumnDefault;

import com.zimpic.backend.entity.type.TruckType;

import jakarta.persistence.*;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "policy_distance_fee_rule", indexes = @Index(name = "idx_distance_active", columnList = "is_active"))
public class DistanceFeeRule {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    @Column(name = "truck_type", nullable = false, unique = true, length = 10)
    private TruckType truckType;
    @Positive @ColumnDefault("20") @Column(name = "base_km", nullable = false)
    private Integer baseKm = 20;
    @Positive @ColumnDefault("10") @Column(name = "unit_km", nullable = false)
    private Integer unitKm = 10;
    @PositiveOrZero @Column(name = "per_unit_amount", nullable = false)
    private Integer perUnitAmount;
    @ColumnDefault("true") @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
```

### 3.8 SpecialItemFee

기존 Django 테이블명에는 `spcial` 오타가 있지만, 새 Spring Boot DB에서는 `special`로 바로잡는다.

```java
package com.zimpic.backend.entity;

import org.hibernate.annotations.ColumnDefault;

import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "policy_special_item_fee", indexes = @Index(name = "idx_special_fee_active", columnList = "is_active"))
public class SpecialItemFee {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "furniture_id", nullable = false, unique = true)
    private Furniture furniture;
    @Column(name = "description", nullable = false, length = 255)
    private String description;
    @PositiveOrZero @Column(name = "unit_amount", nullable = false)
    private Integer unitAmount;
    @ColumnDefault("true") @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
```

## 4. 이미지·탐지 Entity 2개

### 4.1 VisionImage

Spring Boot에서는 실제 파일 저장과 URL 생성을 Service가 담당하고 Entity에는 저장 경로와 원본 파일명만 보관한다.

```java
package com.zimpic.backend.entity;

import java.time.Instant;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "vision_image", indexes = {
    @Index(name = "idx_vision_image_room_type", columnList = "room_type"),
    @Index(name = "idx_vision_image_created_at", columnList = "created_at")
})
public class VisionImage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ColumnDefault("''") @Column(name = "room_type", nullable = false, length = 30)
    private String roomType;
    @Column(name = "image", length = 255)
    private String image;
    @ColumnDefault("''") @Column(name = "image_file_name", nullable = false, length = 255)
    private String imageFileName;
    @ColumnDefault("0") @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
```

### 4.2 VisionDetection

```java
package com.zimpic.backend.entity;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "vision_detection", indexes = {
    @Index(name = "idx_detection_image", columnList = "vision_image_id"),
    @Index(name = "idx_detection_furniture", columnList = "furniture_id"),
    @Index(name = "idx_detection_yolo_class", columnList = "yolo_class"),
    @Index(name = "idx_detection_created_at", columnList = "created_at")
})
public class VisionDetection {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vision_image_id", nullable = false)
    private VisionImage visionImage;
    @Column(name = "yolo_id")
    private Integer yoloId;
    @Column(name = "yolo_class", length = 50)
    private String yoloClass;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "furniture_id")
    private Furniture furniture;
    @Column(name = "confidence", precision = 6, scale = 5)
    private BigDecimal confidence;
    @Column(name = "bbox_x", precision = 8, scale = 6)
    private BigDecimal bboxX;
    @Column(name = "bbox_y", precision = 8, scale = 6)
    private BigDecimal bboxY;
    @Column(name = "bbox_w", precision = 8, scale = 6)
    private BigDecimal bboxW;
    @Column(name = "bbox_h", precision = 8, scale = 6)
    private BigDecimal bboxH;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
```

## 5. 견적 Entity 7개

### 5.1 Estimate

```java
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
    private Boolean destHasElevator;
    @ColumnDefault("false") @Column(name = "dest_use_ladder")
    private Boolean destUseLadder;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
```

### 5.2 EstimateTruckPlan

```java
package com.zimpic.backend.entity;

import java.math.BigDecimal;

import com.zimpic.backend.entity.type.TruckType;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "estimate_truck_plan", indexes = {
    @Index(name = "idx_truck_plan_estimate", columnList = "estimate_id"),
    @Index(name = "idx_truck_plan_type", columnList = "truck_type")
})
public class EstimateTruckPlan {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estimate_id", nullable = false)
    private Estimate estimate;
    @Enumerated(EnumType.STRING)
    @Column(name = "truck_type", nullable = false, length = 20)
    private TruckType truckType;
    @Column(name = "truck_count", nullable = false)
    private Integer truckCount;
    @Column(name = "inner_w_cm", nullable = false, precision = 7, scale = 2)
    private BigDecimal innerWCm;
    @Column(name = "inner_d_cm", nullable = false, precision = 7, scale = 2)
    private BigDecimal innerDCm;
    @Column(name = "inner_h_cm", nullable = false, precision = 7, scale = 2)
    private BigDecimal innerHCm;
    @Column(name = "capacity_cbm", precision = 9, scale = 2)
    private BigDecimal capacityCbm;
    @Column(name = "load_cbm", precision = 9, scale = 2)
    private BigDecimal loadCbm;
    @Column(name = "load_factor_pct", precision = 5, scale = 1)
    private BigDecimal loadFactorPct;
    @Column(name = "remaining_cbm", precision = 9, scale = 2)
    private BigDecimal remainingCbm;
}
```

### 5.3 EstimateRoom

```java
package com.zimpic.backend.entity;

import java.time.Instant;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "estimate_room",
    uniqueConstraints = @UniqueConstraint(name = "uq_estimate_room_estimate_sort_order", columnNames = {"estimate_id", "sort_order"}),
    indexes = @Index(name = "idx_estimate_room_image", columnList = "vision_image_id")
)
public class EstimateRoom {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estimate_id", nullable = false)
    private Estimate estimate;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vision_image_id")
    private VisionImage visionImage;
    @ColumnDefault("''") @Column(name = "room_type", nullable = false, length = 30)
    private String roomType;
    @ColumnDefault("''") @Column(name = "image_url", nullable = false, columnDefinition = "text")
    private String imageUrl;
    @ColumnDefault("0") @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
```

### 5.4 EstimateItem

PostgreSQL JSON 컬럼은 Hibernate의 JSON 타입으로 매핑한다.

```java
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
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
```

### 5.5 EstimatePrice

```java
package com.zimpic.backend.entity;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "estimate_price", indexes = @Index(name = "idx_estimate_price_calculated_at", columnList = "calculated_at"))
public class EstimatePrice {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estimate_id", nullable = false, unique = true)
    private Estimate estimate;
    @Column(name = "distance_km", precision = 7, scale = 2)
    private BigDecimal distanceKm;
    @Column(name = "recommended_ton", precision = 4, scale = 1)
    private BigDecimal recommendedTon;
    @Column(name = "total_cbm", precision = 9, scale = 2)
    private BigDecimal totalCbm;
    @Column(name = "truck_capacity_cbm", precision = 9, scale = 2)
    private BigDecimal truckCapacityCbm;
    @Column(name = "load_factor_pct", precision = 5, scale = 1)
    private BigDecimal loadFactorPct;
    @Column(name = "remaining_cbm", precision = 9, scale = 2)
    private BigDecimal remainingCbm;
    @PositiveOrZero @Column(name = "special_item_count", nullable = false)
    private Integer specialItemCount;
    @PositiveOrZero @Column(name = "boxes_count")
    private Integer boxesCount;
    @Column(name = "boxes_description", length = 255)
    private String boxesDescription;
    @PositiveOrZero @Column(name = "total_amount", nullable = false)
    private Integer totalAmount;
    @CreationTimestamp @Column(name = "calculated_at", nullable = false, updatable = false)
    private Instant calculatedAt;
}
```

### 5.6 EstimatePriceSection

```java
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
```

### 5.7 EstimatePriceDetail

기존 Django 모델명과 테이블명은 각각 `EstimatePriceLine`, `estimate_price_line`이다. 새 Spring Boot DB는 기존 Django DB를 그대로 사용하는 것이 아니므로, 화면의 한 줄이나 일반 텍스트 줄과 혼동되기 쉬운 `Line` 대신 Java Entity와 PostgreSQL 테이블을 모두 `Detail`, `estimate_price_detail`로 통일한다.

```java
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
```

## 6. 작성 순서와 검증

다음 순서로 파일을 하나씩 작성하고 각 묶음이 끝날 때 컴파일한다.

```text
공통 Enum
→ 정책 Entity 8개
→ 이미지·탐지 Entity 2개
→ 견적 Entity 7개
```

PowerShell에서 Java 컴파일만 확인한다.

```powershell
cd spring-backend
.\gradlew.bat classes
```

PostgreSQL과 비밀번호를 준비한 뒤 전체 테스트를 실행한다.

```powershell
$env:DB_PASSWORD="로컬에서_설정한_비밀번호"
.\gradlew.bat test
```

테이블 목록을 확인한다.

```sql
-- 현재 스키마의 테이블 목록
\dt

-- 대표 테이블의 컬럼, 제약조건과 인덱스
\d furniture_rotation
\d policy_truck_spec
\d vision_detection
\d estimate_item
\d estimate_price_detail
```

완료 기준은 다음과 같다.

- `furniture`를 포함해 총 18개 테이블이 생성된다.
- 모든 외래키 컬럼의 NULL 허용 여부가 `docs/07-db-schema.md`와 일치한다.
- 정책 테이블의 unique 제약조건과 `is_active` 기본값이 생성된다.
- `TruckType`이 `T1`, `T2_5` 같은 Java Enum 이름으로 DB에 저장된다.
- `EstimateItem.allowedRotations`가 PostgreSQL JSONB로 저장된다.
- `EstimatePriceDetail`까지 컴파일되고 애플리케이션 컨텍스트에 등록된다.

## 7. 다음 단계

Entity와 테이블 구조 확인이 끝나면 04번 문서 12번으로 돌아가 정책 CSV를 개발용 Seed SQL로 옮긴다. 생성자와 상태 변경 메서드는 실제 Service 구현 시 필요한 값만 받도록 추가하고, Entity 전체 필드를 외부에서 임의 변경하는 Setter는 만들지 않는다.
