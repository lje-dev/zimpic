# 06. 가구 탐지 모델

출처: `ML_DL 3조 프로젝트.xlsx` — 데이터라벨링(구), 훈련 데이터 객체 분석 시트

- 추론 설정: `VISION_IMG_SIZE = 640`, `VISION_CONF = 0.4`
- 클래스 ID → `furniture.yolo_id`로 매핑 (`vision/services/yolo_to_furniture.py`)

## 모델 버전

`backend/vision/weights/`의 weight 파일에 저장된 학습 설정에서 확인한 값이다.

| | best_260125.pt (이전) | **best_260204.pt (서비스 사용)** |
| --- | --- | --- |
| 기반 모델 | YOLOv8n | **YOLO11m** |
| epochs | 50 | **150** |
| imgsz / batch | 640 / 16 | 640 / 16 |
| patience | 100 | 40 |
| 클래스 | 38개 | 38개 (동일) |

아래 [학습 결과 분석](#학습-결과-분석)의 epoch 50 / 150 비교는 이 두 버전의 결과로 보인다 (epoch 수 일치, 원본 시트에는 모델 이름이 없음). `best_260125.pt`는 코드에서 참조하지 않는다.

## 클래스 정의 변화: 53개 → 38개

> 이 절은 현재 쓰지 않는 초기 라벨링 기준을 기록한 것이다. 클래스를 줄인 근거로 남겨 둔다.

라벨링 초기에는 53개 클래스로 정의했다. 사진으로 구분하기 어렵거나 이사 부피에 영향이 작은 항목을 합치거나 빼서 38개로 정리했다. 서비스 클래스 ID는 name_en 알파벳 순서(0~37)다.

| 구 클래스 | 처리 |
| --- | --- |
| refrigerator_sm (미니), refrigerator_kimchi (김치냉장고) | 제거 |
| dryer_sm (미니건조기) | 제거 (washing_machine이 세탁기/건조기 포함) |
| dishwasher_sm | 제거 (dishwasher_lg만 유지) |
| microwave, gas_stove, water_purifier_base/lg | 제거 |
| vacuum_robot, desktop, hometheater | 제거 |
| cabinet_storage, cabinet_drawer, cabinet_display, cabinet_tv | storage_base / storage_lg (높이 기준)로 통합 |
| hanger, dressing_table | 제거 |
| console_table | 협탁/콘솔/트롤리로 범위 확장 |
| 나머지 | 유지 |

### 구 클래스 정의 중 서비스 문구에 남은 기준

| 클래스 | 정의 |
| --- | --- |
| tv_lg | 65인치 이상. 사진으로 구분 어려우면 거실 벽면 대부분을 차지하면 대형 |
| refrigerator_lg | 양문형 또는 문이 3개 이상 |
| bookshelf | 서랍이나 문이 없는 개방형 수납장 |
| closet | 붙박이/빌트인도 옷장으로 간주 → 옮기는 가구가 맞는지 사용자 확인 필요 |
| hanger_module | 행거 3개 이상이 합쳐진, 분해가 필요한 행거 |

## 학습 결과 분석

검증 데이터 210장 기준 클래스별 miss(미탐지) 집계. epoch 50과 150을 비교했다.

### epoch 150 클래스별 성능 (miss rate 오름차순)

| class_id | class | missed / total | miss rate | P | R | mAP50 | mAP50-95 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | air_conditioner_wall | 0 / 1 | 0.00 | 0.225 | 1.000 | 0.332 | 0.166 |
| 5 | bed_stone | 0 / 1 | 0.00 | 0.500 | 1.000 | 0.995 | 0.895 |
| 30 | strongbox | 0 / 8 | 0.00 | 0.743 | 0.875 | 0.919 | 0.727 |
| 26 | sofa_lg | 4 / 134 | 0.03 | 0.699 | 0.903 | 0.823 | 0.659 |
| 7 | chair | 12 / 221 | 0.05 | 0.582 | 0.770 | 0.744 | 0.522 |
| 25 | sitting_table | 9 / 86 | 0.10 | 0.698 | 0.834 | 0.817 | 0.654 |
| 9 | console_table | 9 / 82 | 0.11 | 0.390 | 0.634 | 0.474 | 0.333 |
| 27 | sofa_sm | 12 / 63 | 0.19 | 0.425 | 0.778 | 0.604 | 0.514 |
| 6 | bookshelf | 10 / 51 | 0.20 | 0.478 | 0.510 | 0.476 | 0.319 |
| 0 | air_conditioner_stand | 5 / 18 | 0.28 | 0.670 | 0.667 | 0.763 | 0.494 |
| 29 | storage_lg | 5 / 17 | 0.29 | 0.313 | 0.647 | 0.425 | 0.350 |
| 28 | storage_base | 18 / 60 | 0.30 | 0.390 | 0.517 | 0.476 | 0.381 |
| 33 | tv_base | 9 / 30 | 0.30 | 0.626 | 0.600 | 0.593 | 0.382 |
| 13 | dining_table_sm | 6 / 20 | 0.30 | 0.367 | 0.700 | 0.496 | 0.424 |
| 34 | tv_lg | 7 / 21 | 0.33 | 0.557 | 0.762 | 0.711 | 0.559 |
| 12 | dining_table_md | 6 / 18 | 0.33 | 0.400 | 0.722 | 0.397 | 0.286 |
| 24 | refrigerator_lg | 3 / 9 | 0.33 | 0.456 | 0.556 | 0.545 | 0.432 |
| 3 | bed_lg | 1 / 3 | 0.33 | 0.285 | 0.667 | 0.699 | 0.499 |
| 4 | bed_sm | 6 / 17 | 0.35 | 0.641 | 0.588 | 0.613 | 0.409 |
| 17 | flowerpot | 33 / 89 | 0.37 | 0.517 | 0.337 | 0.427 | 0.229 |
| 20 | piano_digital | 2 / 4 | 0.50 | 1.000 | 0.624 | 0.875 | 0.716 |
| 10 | desk | 23 / 37 | 0.62 | 0.564 | 0.350 | 0.450 | 0.365 |
| 11 | dining_table_lg | 5 / 6 | 0.83 | 0.153 | 0.167 | 0.178 | 0.171 |
| 2 | air_purifier | 6 / 6 | 1.00 | 0 | 0 | 0 | 0 |
| 23 | refrigerator_base | 6 / 6 | 1.00 | 0 | 0 | 0 | 0 |
| 22 | recliner | 3 / 3 | 1.00 | 0 | 0 | 0 | 0 |
| 16 | fitness | 2 / 2 | 1.00 | 0.403 | 0.500 | 0.332 | 0.233 |
| 8 | closet | 1 / 1 | 1.00 | 0 | 0 | 0 | 0 |
| 19 | piano_classic | 1 / 1 | 1.00 | 0 | 0 | 0 | 0 |
| 35 | vacuum_cleaner | 1 / 1 | 1.00 | 0 | 0 | 0 | 0 |
| 37 | washing_machine | 1 / 1 | 1.00 | 0 | 0 | 0 | 0 |

검증셋에 없던 클래스: dishwasher_lg(14), fishbowl(15), hanger_module(18), pot(21), styler(31), treadmill(32), washing_dryer(36)

### epoch 50 → 150 변화 (miss rate)

| class | epoch 50 | epoch 150 |
| --- | --- | --- |
| air_conditioner_stand | 0.56 | 0.28 |
| bed_sm | 0.76 | 0.35 |
| bookshelf | 0.49 | 0.20 |
| storage_lg | 0.76 | 0.29 |
| tv_lg | 0.71 | 0.33 |
| dining_table_md | 0.44 | 0.33 |
| flowerpot | 0.31 | 0.37 |
| storage_base | 0.27 | 0.30 |
| desk | 0.62 | 0.62 |

### 결론 / 액션

- 샘플이 많은 클래스(sofa_lg, chair, sitting_table)는 안정적이다.
- **검증 샘플 수(total)가 작은 클래스**부터 이미지 추가 수집과 라벨링을 하기로 했다 (가전류, 특수가구, 대형 식탁).
- desk, flowerpot, storage_base는 epoch을 늘려도 개선이 없다 → 데이터 품질과 클래스 경계를 점검해야 한다.
- 탐지되지 않은 가구는 프론트의 **가구 직접 추가** 기능으로 보완한다.
