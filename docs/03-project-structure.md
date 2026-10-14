# 03. 프로젝트 구조 · 실행 방법

## 전체 구조

```
zimpic
├── backend/      # Django + DRF
│   ├── config/   # settings, urls
│   ├── policy/   # 어드민이 관리하는 기준 데이터(가구/트럭/요금표/룰)
│   ├── vision/   # 이미지 업로드 + YOLO 추론 + 가구 매핑
│   ├── estimates/# 톤수 계산 + 비용 계산 + 견적 저장/응답
│   ├── example/  # 샘플 앱 (urls 미연결)
│   ├── static/, templates/   # 어드민 커스텀, drf-yasg
│   └── manage.py
├── frontend/     # React 19 + Vite
└── docs/
```

## backend

### URL 라우팅 (`config/urls.py`)

| 경로 | 앱 |
| --- | --- |
| `/admin/` | Django 어드민 (정책 데이터 관리) |
| `/api/policy/` | policy (GET) |
| `/api/vision/` | vision (POST) |
| `/api/estimates/` | estimates (POST) |
| `/swagger/`, `/docs/` | Swagger UI / Redoc |
| `/media/` | 업로드 이미지 (DEBUG일 때만 서빙) |

### policy/

어드민이 관리하는 모든 기준 데이터.

```
policy/
  models.py           # Furniture, FurnitureRotation, BoxRule, TruckSpec,
                      # BasePrice, LadderFeeRule, StairsFeeRule,
                      # DistanceFeeRule, SpecialItemFee
  data/*.csv          # 초기 정책 데이터
  management/commands/
    load_all_policy.py  # CSV 전체 적재
    load_*.py           # 테이블별 적재
```

### vision/

```
vision/
  serializers.py
  views.py                    # 요청 → 파이프라인 호출 → 응답
  services/
    file_path_utils.py        # 이미지 저장 경로 설정
    model_inference.py        # 모델 추론
    yolo_to_furniture.py      # 탐지 결과(yolo_id) → Furniture 매핑
    pipeline.py               # 요청 파싱 → 모델 추론 → 가구 매핑
  weights/
    best_260204.pt            # 현재 사용 YOLO 모델 (settings.VISION_MODEL_PATH)
```

- 유저 이미지 저장
- 모델 추론(가구 탐지)
- 탐지 결과(이미지, bbox) + DB 가구 기본정보(W/D/H) → 합쳐서 프론트 반환

### estimates/

```
estimates/
  serializers.py
  views.py                 # 요청 → 응답
  services/
    packing.py             # 유저 입력 사이즈 + padding_cm → packed_size
    truck_plan.py          # 추천 톤수 & 트럭 플랜 산출
    distance.py            # 출발/도착 주소 → 거리(km)
    pricing_distance.py    # 거리 비용
    pricing_access.py      # 접근 비용(사다리/계단)
    pricing_special.py     # 특수가구 비용
    pricing.py             # 비용 오케스트레이터(기본+거리+접근+특수)
    estimate_create.py     # 견적 생성 전체 오케스트레이션
    context.py
```

`estimate_create.py`가 입력 저장 → 부피·톤수 계산 → 거리 조회 → 비용 계산 → 결과 저장 순서로 실행한다. 계산 규칙은 [04. 견적 산출 정책](04-estimate-policy.md) 참고.

## frontend

React 19 + Vite, 상태관리 zustand, UI react-bootstrap, 3D는 three / @react-three/fiber.

```
src/
  pages/
    MainPage.jsx       # /            랜딩
    HomePage.jsx       # /HomePage    이사 종류·평수·방 사진 입력
    AICheckPage.jsx    # /AICheckPage AI 탐지 결과 확인/수정
    AddressPage.jsx    # /AddressPage 주소·층수·엘리베이터/사다리
    ResultPage.jsx     # /ResultPage  견적 결과 + 3D 적재
  components/
    Main/              # 랜딩 섹션
    estimate/home|ai|address|result/
    estimate/result/logic/   # 3D 적재(bin packing) 로직
    layout/, common/
  services/            # visionApi, estimateApi, estimatePayload, policyApi
  store/estimateStore.js     # zustand (persist)
  utils/imageDB.js           # 방 이미지 IndexedDB 저장
```

API는 Vite proxy 전제로 상대경로(`/api/...`) 호출.

## 개발 환경 세팅

### 백엔드

```bash
cd backend
python -m venv venv
# Windows
venv\Scripts\activate
# mac
source venv/bin/activate

pip install django djangorestframework drf-yasg django-cors-headers mysqlclient \
            python-dotenv torch ultralytics requests django-filter
```

> 라이브러리 설치 후 필요한 것은 `INSTALLED_APPS`에 추가 (`corsheaders`, `rest_framework`, `drf_yasg`, 각 앱).

### 마이그레이션 & 정책 데이터

```bash
python manage.py makemigrations
python manage.py migrate
python manage.py load_all_policy   # policy/data/*.csv 적재
```

### 프론트엔드

```bash
cd frontend
npm install
npm run dev     # http://localhost:5173
```

### settings.py 주요 항목

`backend/config/settings.py`는 gitignore 대상. 팀원은 각자 로컬에 생성한다. 서비스 키는 `settings_local.py` / `.env`에 넣는다.

| 항목 | 값 |
| --- | --- |
| `INSTALLED_APPS` | `corsheaders`, `rest_framework`, `drf_yasg`, `policy.apps.PolicyConfig`, `vision.apps.VisionConfig`, `estimates.apps.EstimatesConfig` |
| `MIDDLEWARE` | 맨 앞에 `corsheaders.middleware.CorsMiddleware` |
| `DATABASES` | MySQL, DB명 `zimpic`, charset utf8 (계정은 각자 로컬) |
| `LANGUAGE_CODE` / `TIME_ZONE` | `ko-kr` / `Asia/Seoul` |
| `MEDIA_URL` / `MEDIA_ROOT` | `/media/` / `BASE_DIR / "media"` |
| `VISION_MODEL_PATH` | `BASE_DIR / "vision" / "weights" / "best_260204.pt"` |
| `VISION_DEVICE` | `cuda` 가능 시 cuda, 아니면 cpu |
| `VISION_IMG_SIZE` / `VISION_CONF` | `640` / `0.4` |
| `CORS_ALLOWED_ORIGINS`, `CSRF_TRUSTED_ORIGINS` | `http://localhost:5173`, `http://127.0.0.1:5173` |
| `REST_FRAMEWORK` | `PageNumberPagination`(10), `AllowAny`, `SessionAuthentication` |

## Git

`.gitignore` 주요 항목:

```gitignore
backend/venv/
__pycache__/
*.pyc
backend/config/settings.py
backend/config/settings_local.py
backend/.env
backend/media/
backend/staticfiles/
frontend/node_modules/
frontend/dist/
.env
.vscode/
.idea/
```

이미 추적 중인 settings.py 제외:

```bash
git rm --cached backend/config/settings.py
git commit -m "ignore settings.py"
```
