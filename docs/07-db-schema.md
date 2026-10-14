# 07. DB 스키마

출처: `ML_DL 3조 프로젝트.xlsx` — 가구 테이블, 비용 견적 데이터 시트. 모델: `backend/*/models.py`

```
policy (기준 데이터, 어드민 관리)
  furniture ─┬─< furniture_rotation
             └─< policy_spcial_item_fee
  policy_box_rule, policy_truck_spec, policy_base_price,
  policy_ladder_fee_rule, policy_stairs_fee_rule, policy_distance_fee_rule

vision (탐지 결과)
  vision_image ─< vision_detection >─ furniture

estimates (견적 결과 스냅샷)
  estimate ─┬─< estimate_truck_plan
            ├─< estimate_room ─< estimate_item >─ furniture
            │        └── vision_image
            └── estimate_price ─< estimate_price_section ─< estimate_price_line
```

> 테이블 구조만 다룬다. 정책 요금표 값은 [04. 견적 산출 정책](04-estimate-policy.md), 가구 데이터 값은 [05. 가구 데이터](05-furniture-data.md) 참고.

---

## policy

### furniture

| 컬럼 | 타입 | NULL | 기본값 | 설명 / 제약 |
| --- | --- | --- | --- | --- |
| id | BIGINT | NO | AUTO | PK |
| category | VARCHAR(20) | NO | | 일반가전 / 주방가전 / 일반가구 / 특수가구 / 기타 |
| yolo_id | INT | YES | | YOLO 클래스 ID (모델 결과 매핑 키), UNIQUE |
| name_en | VARCHAR(64) | NO | | 내부 코드 (snake_case), UNIQUE |
| name_kr | VARCHAR(64) | NO | | 화면 표시명 |
| description | VARCHAR(255) | YES | NULL | 가구 부피 계산 설명 (사용자 노출 문구) |
| reference_model | VARCHAR(255) | YES | NULL | 기준으로 삼은 실제 제품명 |
| reference_url | VARCHAR(500) | YES | NULL | 제품 링크 |
| width_cm / depth_cm / height_cm | INT | NO | | 기본 W/D/H, > 0 |
| padding_cm | DECIMAL(4,1) | NO | 1.0 | 포장재 두께(사방 적용), 0.0~10.0 |
| needs_disassembly | BOOLEAN | NO | 0 | 분해/재조립 필요 여부 |
| stackable | BOOLEAN | NO | 0 | 이 물건을 다른 물건 **위에** 올릴 수 있는지 (예: 금고는 밑 물건이 망가짐 → 0) |
| can_stack_on_top | BOOLEAN | NO | 0 | 이 물건 **위에** 다른 물건을 올릴 수 있는지 (예: 어항 → 0) |

padding_cm 기준값 → [05. 가구 데이터](05-furniture-data.md#포장재-두께-padding_cm)

### furniture_rotation

| 컬럼 | 타입 | NULL | 설명 |
| --- | --- | --- | --- |
| id | BIGINT | NO | PK |
| furniture_id | BIGINT | NO | FK → furniture.id |
| orientation_code | CHAR(3) | NO | WHD, WDH, HWD, HDW, DWH, DHW |
| note | VARCHAR(100) | YES | 회전 설명 |

UNIQUE(furniture_id, orientation_code). 코드 의미 → [05. 가구 데이터](05-furniture-data.md#회전-코드-orientation_code)

### 정책 요금 테이블

| 테이블 | 주요 컬럼 | 키 |
| --- | --- | --- |
| policy_box_rule | area_min_py, area_max_py, boxes_min/max/avg, note | 평수 구간 겹침 금지 (clean 검증) |
| policy_truck_spec | truck_type, name, body_type, inner_width/length/height_cm, cbm_geom | truck_type UNIQUE |
| policy_base_price | move_type, truck_type, base_amount, included_workers, note | UNIQUE(move_type, truck_type) |
| policy_ladder_fee_rule | ladder_truck_group, floor_from, floor_to, base_amount | UNIQUE(group, from, to) |
| policy_stairs_fee_rule | floor_from, floor_to, per_floor_amount | UNIQUE(from, to) |
| policy_distance_fee_rule | truck_type, base_km, unit_km, per_unit_amount | truck_type UNIQUE |
| policy_spcial_item_fee | furniture_id, description, unit_amount | furniture_id UNIQUE |

모든 정책 테이블에 `is_active` 있음.

---

## vision

### vision_image (업로드 이미지)

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | BIGINT PK | |
| room_type | VARCHAR(30) | LIVING / ROOM1 … |
| image_file_name | VARCHAR(255) | 파일명 |
| image | VARCHAR(255) | 파일 경로 (ImageField) |
| sort_order | INT | 정렬 |
| created_at | DATETIME | |

### vision_detection (객체 탐지 결과)

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | BIGINT PK | detection id |
| vision_image_id | BIGINT FK, IDX | → vision_image.id |
| yolo_id | INT | 탐지 순번(선택) |
| yolo_class | VARCHAR(50) / INT | 클래스 |
| furniture_id | BIGINT FK, IDX | → furniture.id (매핑 성공 시) |
| confidence | DECIMAL(6,5) | score |
| bbox_x / bbox_y | DECIMAL(8,6) | YOLO 정규화 center x / y |
| bbox_w / bbox_h | DECIMAL(8,6) | YOLO 정규화 w / h |
| created_at | DATETIME | |

---

## estimates

### estimate

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | BIGINT PK | 견적 ID |
| move_type | VARCHAR(20) | GENERAL / PACKING |
| area | INT | 평수 |
| origin_address | TEXT | 출발지 |
| origin_floor | INT | |
| origin_has_elevator | BOOL | |
| origin_use_ladder | BOOL | |
| dest_address | TEXT | 도착지 |
| dest_floor | INT | |
| dest_has_elevator | BOOL | |
| dest_use_ladder | BOOL | |
| created_at | DATETIME | |

### estimate_truck_plan

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | BIGINT PK | |
| estimate_id | BIGINT FK, IDX | → estimate.id |
| truck_type | VARCHAR(20) | 5T / 1T … |
| truck_count | INT | 대수 (현재 1 row = 1대) |
| inner_w_cm / inner_d_cm / inner_h_cm | DECIMAL(7,2) | 적재함 내측 치수 |
| capacity_cbm | DECIMAL(9,2) | 트럭 용량 |
| load_cbm | DECIMAL(9,2) | 적재량 |
| load_factor_pct | DECIMAL(5,1) | 적재율(%) |
| remaining_cbm | DECIMAL(9,2) | 잔여 |

### estimate_room

UNIQUE(estimate_id, sort_order)

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | BIGINT PK | |
| estimate_id | BIGINT FK, IDX | → estimate.id |
| vision_image_id | BIGINT FK, IDX | → vision_image.id |
| room_type | VARCHAR(30) | 응답용 스냅샷(선택) |
| image_url | TEXT | 응답용 스냅샷(선택) |
| sort_order | INT | |
| created_at | DATETIME | |

### estimate_item

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | BIGINT PK | |
| estimate_room_id | BIGINT FK, IDX | → estimate_room.id |
| item_id | VARCHAR(32) UQ | uuid |
| furniture_id | BIGINT FK, IDX | → furniture.id |
| w_cm / d_cm / h_cm | DECIMAL(7,2) | 사용자 입력 사이즈 |
| padding_cm | DECIMAL(4,1) | |
| packed_w_cm / packed_d_cm / packed_h_cm | DECIMAL(7,2) | 포장 후 사이즈 |
| stackable | BOOL | |
| can_stack_on_top | BOOL | |
| allowed_rotations_json | JSON | `["WDH","DWH"]` |
| needs_disassembly | BOOL | |
| created_at | DATETIME | |

### estimate_price (policy 계산 결과 스냅샷, 견적당 1개)

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | BIGINT PK | |
| estimate_id | BIGINT FK, UQ | → estimate.id |
| distance_km | DECIMAL(7,2) | 거리 |
| recommended_ton | DECIMAL(4,1) | 추천 톤수 |
| total_cbm | DECIMAL(9,2) | 총 부피 |
| truck_capacity_cbm | DECIMAL(9,2) | 트럭 총 용량 |
| load_factor_pct | DECIMAL(5,1) | 총 적재율 |
| remaining_cbm | DECIMAL(9,2) | 총 잔여 |
| special_item_count | INT | 특수짐 개수 |
| boxes_count | INT | 평수 기준 박스 개수 |
| boxes_description | VARCHAR(255) | 박스 산정 근거 |
| total_amount | INT | 총합 |
| calculated_at | DATETIME | |

### estimate_price_section

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | BIGINT PK | |
| estimate_price_id | BIGINT FK, IDX | → estimate_price.id |
| key | VARCHAR(20) IDX | BASE / LADDER / STAIRS / DISTANCE / SPECIAL |
| title | VARCHAR(50) | "사다리차 비용" |
| amount | INT | 섹션 합계 |
| description | VARCHAR(255) NULL | BASE / DISTANCE 설명 |

### estimate_price_line

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| id | BIGINT PK | |
| estimate_price_section_id | BIGINT FK, IDX | → estimate_price_section.id |
| scope | VARCHAR(10) NULL | ORIGIN / DEST |
| furniture_id | BIGINT FK NULL | SPECIAL일 때만 |
| name_kr | VARCHAR(64) NULL | "어항" 등 |
| amount | INT | 라인 금액 |
| description | VARCHAR(255) NULL | "출발지 12층 기준" |
