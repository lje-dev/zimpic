# 08. API

| Method | Endpoint | 설명 |
| --- | --- | --- |
| POST | `/api/vision/` | 방 이미지 업로드 + 가구 탐지 |
| POST | `/api/estimates/` | 견적 요청 |
| GET | `/api/policy/furniture/` | 가구 정책 목록 (수동 가구 추가용) |

Swagger: `/swagger/`, Redoc: `/docs/`

---

## 1. 이미지 업로드 `POST /api/vision/`

### Request — `multipart/form-data`

| KEY | TYPE | VALUE |
| --- | --- | --- |
| `rooms` | Text (JSON 문자열) | 아래 JSON |
| `files` | File | living_1.jpg |
| `files` | File | room1_1.jpg |

`rooms`:

```json
[
  { "room_type": "LIVING", "file_index": 0, "sort_order": 1 },
  { "room_type": "ROOM1",  "file_index": 1, "sort_order": 2 }
]
```

- `file_index`: `files` 배열에서의 인덱스
- `room_type`: `LIVING`, `KITCHEN`, `BALCONY`, `UTILITY`, `ROOM{n}`, `UNKNOWN`

### Response

```json
{
  "results": [
    {
      "vision_image_id": 16,
      "room_type": "LIVING",
      "image_url": "http://localhost:8000/media/vision/20260126023310_kw_00429.jpg",
      "detections": [
        {
          "detection_id": 23,
          "furniture_id": 20,
          "name_kr": "소파(대형)",
          "name_en": "sofa_lg",
          "category": "GENERAL_FURNITURE",
          "confidence": 0.7790692448616028,
          "bbox": {
            "x": 0.28096396923065187,
            "y": 0.7634932518005371,
            "w": 0.41866440773010255,
            "h": 0.29370765686035155
          },
          "description": "1인용을 제외한 2인 이상 소파는 모두 대형 소파로 계산했어요.",
          "reference_model": "이케아 KIVIK 쉬비크",
          "reference_url": "https://www.ikea.com/kr/ko/p/kivik-2-seat-sofa-tresund-light-beige-s59482822/",
          "guide_size_cm": { "width_cm": 190, "depth_cm": 95, "height_cm": 83 },
          "needs_disassembly": false
        }
      ]
    },
    {
      "vision_image_id": 17,
      "room_type": "ROOM1",
      "image_url": "http://localhost:8000/media/vision/20260126023310_je_01770.jpg",
      "detections": [
        {
          "detection_id": 26,
          "furniture_id": 15,
          "name_kr": "책상",
          "name_en": "desk",
          "category": "GENERAL_FURNITURE",
          "confidence": 0.531928300857544,
          "bbox": { "x": 0.7703, "y": 0.6752, "w": 0.4535, "h": 0.2256 },
          "description": "다리가 4개인 직사각형 형태의 일반 책상을 기준으로 계산했어요.",
          "reference_model": "이케아 MICKE 미케",
          "reference_url": "https://www.ikea.com/kr/ko/p/micke-desk-white-80354276/",
          "guide_size_cm": { "width_cm": 105, "depth_cm": 50, "height_cm": 75 },
          "needs_disassembly": false
        }
      ]
    }
  ]
}
```

- `bbox`: 이미지 크기 대비 정규화 좌표(0~1)
- `category`: `GENERAL_APPLIANCE`, `KITCHEN_APPLIANCE`, `GENERAL_FURNITURE`, `SPECIAL_FURNITURE`, `ETC`
- `guide_size_cm`: policy `Furniture`의 기준 사이즈. 사용자가 프론트에서 수정 가능

---

## 2. 견적 요청 `POST /api/estimates/`

### Request — `application/json`

```json
{
  "move_type": "PACKING",
  "area": 50,

  "origin_address": "부산 부산진구 중앙대로 797",
  "origin_floor": 10,
  "origin_has_elevator": false,
  "origin_use_ladder": true,

  "dest_address": "부산 강서구 가덕해안로 3",
  "dest_floor": 4,
  "dest_has_elevator": false,
  "dest_use_ladder": false,

  "rooms": [
    {
      "vision_image_id": 16,
      "sort_order": 0,
      "items": [
        {
          "furniture_id": 4,
          "size_cm": { "w_cm": 190, "d_cm": 95, "h_cm": 83 },
          "needs_disassembly": true
        }
      ]
    },
    {
      "vision_image_id": 17,
      "sort_order": 1,
      "items": [
        {
          "furniture_id": 26,
          "size_cm": { "w_cm": 46, "d_cm": 54, "h_cm": 80 },
          "needs_disassembly": false
        },
        {
          "furniture_id": 15,
          "size_cm": { "w_cm": 105, "d_cm": 50, "h_cm": 75 },
          "needs_disassembly": false
        }
      ]
    }
  ]
}
```

| 필드 | 설명 |
| --- | --- |
| `move_type` | `GENERAL`(일반이사) / `PACKING`(포장이사) |
| `area` | 평수 → 박스 개수 산정 |
| `*_has_elevator`, `*_use_ladder` | 접근 비용 판단 ([04. 견적 산출 정책](04-estimate-policy.md#접근-비용)) |
| `rooms[].items[]` | 사용자가 확인/수정한 가구 목록 |

### Response

```json
{
  "estimate_id": 65,
  "summary": {
    "move_type": "PACKING",
    "area": 50,
    "origin_address": "부산 부산진구 중앙대로 797",
    "origin_floor": 10,
    "origin_has_elevator": false,
    "origin_use_ladder": true,
    "dest_address": "부산 강서구 가덕해안로 3",
    "dest_floor": 4,
    "dest_has_elevator": false,
    "dest_use_ladder": false,

    "distance_km": "30.94",
    "recommended_ton": "2.5",
    "special_item_count": 2,
    "boxes_count": 100,
    "boxes_description": "50평 기준, 이사 박스(5호) 평균 100개를 기준으로 계산했어요.",
    "total_cbm": "13.36",
    "truck_capacity_cbm": "14.00",
    "load_factor_pct": "95.4",
    "remaining_cbm": "0.64",

    "truck_plan": [
      {
        "truck_type": "2_5T",
        "truck_count": 1,
        "inner_w_cm": "190.00",
        "inner_d_cm": "430.00",
        "inner_h_cm": "180.00",
        "capacity_cbm": "14.00",
        "load_cbm": "13.36",
        "load_factor_pct": "95.4",
        "remaining_cbm": "0.64"
      }
    ]
  },
  "rooms": [
    {
      "vision_image_id": 17,
      "room_type": "",
      "image_url": "",
      "sort_order": 1,
      "items": [
        {
          "item_id": "45ca0074-9b3b-44a6-8a48-73ae090913c5",
          "furniture_id": 15,
          "name_kr": "책상",
          "w_cm": "105.00",
          "d_cm": "50.00",
          "h_cm": "75.00",
          "padding_cm": "1.0",
          "packed_w_cm": "107.00",
          "packed_d_cm": "52.00",
          "packed_h_cm": "77.00",
          "stackable": false,
          "can_stack_on_top": true,
          "allowed_rotations": ["DWH", "WDH"],
          "needs_disassembly": false
        }
      ]
    }
  ],
  "pricing": {
    "total_amount": 1375000,
    "sections": [
      { "key": "BASE", "title": "기본 요금", "amount": 1000000, "description": "포장이사 2.5톤 기준" },
      {
        "key": "LADDER", "title": "사다리차 추가 비용", "amount": 180000,
        "lines": [{ "scope": "ORIGIN", "amount": 180000, "description": "5톤 기준 출발지 10층 사다리차 비용" }]
      },
      {
        "key": "STAIRS", "title": "계단 추가 비용", "amount": 45000,
        "lines": [{ "scope": "DEST", "amount": 45000, "description": "5톤 기준 도착지 4층 계단 비용" }]
      },
      { "key": "DISTANCE", "title": "거리 추가 비용", "amount": 50000, "description": "30.94km기준, 20km 초과 시 10km당 25,000원 추가" },
      {
        "key": "SPECIAL", "title": "특수가구 추가 비용", "amount": 100000,
        "lines": [{ "furniture_id": 4, "amount": 100000, "description": "대형 냉장고 분해/조립 비용 개당 50000원 추가" }]
      }
    ]
  }
}
```

### 응답 필드 메모

- `packed_*_cm` = 입력 사이즈 + `padding_cm × 2` (사방 포장재)
- `allowed_rotations`, `stackable`, `can_stack_on_top`: 적재 규칙 → [05. 가구 데이터](05-furniture-data.md)
- `load_factor_pct` = `total_cbm / truck_capacity_cbm × 100`
- `pricing.sections[].key`: `BASE`, `LADDER`, `STAIRS`, `DISTANCE`, `SPECIAL`. `LADDER`/`STAIRS`/`SPECIAL`은 `lines`로 상세 제공
- 숫자형 Decimal 값은 문자열로 직렬화됨

---

## 3. 가구 정책 목록 `GET /api/policy/furniture/`

policy `Furniture` 목록. 프론트에서 AI가 못 잡은 가구를 수동 추가할 때 사용.
