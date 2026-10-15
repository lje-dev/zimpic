# 2. FastAPI YOLO 서버 분리 가이드

## 목표

기존 Django 내부에서 실행하던 YOLO 추론을 독립된 FastAPI 서버로 분리한다.

이 단계에서는 Docker를 사용하지 않는다. 로컬에서 FastAPI를 직접 실행하고 이미지 한 장을 전송해 탐지 결과 JSON을 받는 것까지 확인한다.

```text
이미지 요청
   ↓
FastAPI
   ↓
YOLO 추론
   ↓
탐지 결과 JSON
```

## FastAPI로 옮길 범위

기존 코드 중 YOLO 모델 로딩과 추론만 옮긴다.

- 이동 대상: `backend/vision/services/model_inference.py`
- 이동하지 않는 대상:
  - 이미지와 탐지 결과 DB 저장
  - YOLO 클래스와 Furniture 매핑
  - 에어컨 실외기 추가
  - 가구 기본 크기 조회
  - 견적 계산

이동하지 않는 기능은 이후 Spring Boot가 담당한다. FastAPI는 PostgreSQL에 직접 접근하지 않는다.

## 1. 디렉터리 생성

프로젝트 루트에 다음 구조를 만든다.

```text
yolo/
├── app/
│   ├── __init__.py
│   ├── main.py
│   ├── schemas.py          # API 요청·응답 형식 정의
│   ├── yolo_service.py     # YOLO 로딩 및 추론
│   └── routers/
│       ├── __init__.py
│       ├── health.py       # 상태 확인 API
│       └── inference.py    # 이미지 업로드 및 추론 API
├── tests/
│   └── test_health.py
├── weights/
│   └── best_260204.pt
└── requirements.txt
```

기존 `backend/`는 비교를 위해 그대로 유지한다. 모델 파일도 기존 파일을 삭제하지 않고 `yolo/weights/`에 복사한다.

## 2. Python 3.11 설치

YOLO와 PyTorch 등 머신러닝 라이브러리의 호환성을 고려해 Python 3.11을 사용한다. 시스템에 다른 Python 버전이 설치되어 있어도 3.11을 함께 설치할 수 있다.

### Windows

Python 설치 관리자가 준비되어 있다면 PowerShell에서 설치한다.

```powershell
py install 3.11
```

`py` 명령을 사용할 수 없다면 Microsoft Store 또는 [Python 공식 다운로드 페이지](https://www.python.org/downloads/)에서 Python 설치 관리자를 먼저 설치한다.

설치된 버전을 확인한다.

```powershell
py -3.11 --version
```

### macOS

[Homebrew](https://brew.sh/)가 설치되어 있다면 터미널에서 설치한다.

```bash
brew install python@3.11
```

설치된 버전을 확인한다.

```bash
python3.11 --version
```

두 운영체제 모두 `Python 3.11.x`가 출력되면 다음 단계로 진행한다.

## 3. Python 가상환경 생성 및 활성화

운영체제에 맞는 터미널 명령을 실행한다. 가상환경은 시스템 Python과 프로젝트에서 사용하는 라이브러리를 분리한다.

### Windows PowerShell

```powershell
# yolo 프로젝트 디렉터리로 이동
cd yolo

# Python 3.11을 사용하는 .venv 가상환경 생성
py -3.11 -m venv .venv

# 현재 PowerShell에서 가상환경 활성화
.\.venv\Scripts\Activate.ps1
```

### macOS

```bash
# yolo 프로젝트 디렉터리로 이동
cd yolo

# Python 3.11을 사용하는 .venv 가상환경 생성
python3.11 -m venv .venv

# 현재 터미널에서 가상환경 활성화
source .venv/bin/activate
```

활성화는 가상환경 폴더 안으로 이동하는 것이 아니라, 현재 터미널이 `.venv` 안의 Python과 설치된 라이브러리를 사용하도록 설정하는 과정이다. 가상환경이 활성화되면 일반적으로 터미널 앞에 `(.venv)`가 표시된다.

## 4. 라이브러리 정의 및 설치

`requirements.txt`에 우선 다음 의존성을 작성한다.

```text
fastapi[standard]
python-multipart
python-dotenv
ultralytics
pillow
pytest
httpx
```

설치한다.

```powershell
pip install -r requirements.txt
```

`python-multipart`는 `multipart/form-data` 형식의 이미지 업로드를 받는 데 필요하고, `python-dotenv`는 로컬 `.env` 파일의 설정값을 불러오는 데 사용한다.

처음 기능을 확인한 뒤 설치된 버전을 고정한다. 버전 고정 방법은 구현 시 선택하되, 운영 이미지가 매번 다른 버전으로 설치되지 않도록 해야 한다.

## 5. 최소 FastAPI 애플리케이션 실행 확인

YOLO 코드를 작성하기 전에 FastAPI가 정상적으로 설치되었고 서버를 실행할 수 있는지 먼저 확인한다.

`app/main.py`를 다음과 같이 작성한다.

```python
from fastapi import FastAPI


# FastAPI 애플리케이션 객체를 생성한다.
app = FastAPI()


# GET /hello 요청을 처리하는 임시 확인용 API다.
@app.get("/hello")
def hello():
    return {"message": "hello"}
```

여기서 `app/`은 Python 패키지 디렉터리이고, `app = FastAPI()`의 `app`은 FastAPI 애플리케이션 객체를 담은 변수다. 이름은 같지만 서로 다른 대상이다.

`@app.get("/hello")`처럼 함수 위에 `@`로 시작하는 Python 문법을 **데코레이터(decorator)**라고 한다. Java의 어노테이션과 비슷해 보이지만 Python에서는 데코레이터라고 부른다.

여기서는 `FastAPI()` 객체를 담은 변수 `app`에 `GET /hello`를 등록하고, 요청이 들어오면 바로 아래 `hello()` 함수를 실행하도록 설정한다. 데코레이터 앞에는 객체를 담은 변수 이름을 사용하므로, 변수 이름이 `api`라면 `@api.get(...)`으로 작성해야 한다.

가상환경이 활성화된 상태에서 `yolo/` 디렉터리로 이동해 개발 서버를 실행한다.

```powershell
fastapi dev app/main.py
```

브라우저에서 다음 주소를 열거나 PowerShell에서 호출한다.

```text
http://127.0.0.1:8000/hello
```

```powershell
curl.exe http://127.0.0.1:8000/hello
```

예상 응답은 다음과 같다.

```json
{"message":"hello"}
```

Swagger 화면도 확인한다.

```text
http://127.0.0.1:8000/docs
```

이 단계의 `/hello`는 FastAPI 설치와 실행만 확인하기 위한 임시 API다. 이후 단계에서 `main.py`에 lifespan과 라우터 등록을 추가해 실제 애플리케이션 진입점으로 완성한다.

## 6. 응답 형식 정의

`app/schemas.py`에 FastAPI가 반환할 JSON 구조를 정의한다.

```python
from pydantic import BaseModel, Field


class BoundingBox(BaseModel):
    x: float = Field(ge=0, le=1)
    y: float = Field(ge=0, le=1)
    width: float = Field(ge=0, le=1)
    height: float = Field(ge=0, le=1)


class Detection(BaseModel):
    class_id: int
    class_name: str
    confidence: float = Field(ge=0, le=1)
    bbox: BoundingBox


class InferenceResponse(BaseModel):
    model_version: str
    detections: list[Detection]
```

기존 Django 응답의 `yolo_id`는 FastAPI 응답에서 `class_id`로 표현한다. Spring Boot 구현 전에 최종 필드 이름을 한 번 확정하고 이후에는 양쪽에서 동일하게 사용한다.

## 7. YOLO 추론 코드 분리

`app/yolo_service.py`에는 Django 설정과 DB 코드 없이 모델 로딩과 추론만 작성한다.

기존 구현은 `backend/vision/services/model_inference.py`의 `_get_model()`과 `run_vision_inference()`에서 확인할 수 있다. 새 FastAPI 서비스에서는 Django의 `settings` 의존성을 제거하고, 모델 경로와 추론 설정을 함수 인자로 전달한다.

```python
from pathlib import Path

from ultralytics import YOLO
from ultralytics.utils import ThreadingLocked


def load_model(model_path: Path, device: str):
    # YOLO 모델 불러오기
    model = YOLO(str(model_path))

    # 추론에 CPU 또는 GPU 사용 설정
    model.to(device)
    return model


# 동시에 하나의 요청만 추론하도록 설정
@ThreadingLocked()
def run_inference(model, image_path: Path, image_size: int, confidence: float):
    # 이미지에서 가구 탐지
    results = model.predict(
        source=str(image_path),
        imgsz=image_size,
        conf=confidence,
        # 결과 이미지 저장 안 함
        save=False,
        # 상세 로그 출력 안 함
        verbose=False,
    )

    # 탐지된 가구가 없으면 빈 목록 반환
    if not results or results[0].boxes is None:
        return []

    # 이미지 한 장의 탐지 결과 가져오기
    result = results[0]

    # result                  이미지 한 장의 YOLO 추론 결과
    # ├── orig_shape          원본 이미지 크기 (높이, 너비)
    # ├── names               클래스 번호와 이름
    # └── boxes               탐지된 바운딩박스 정보
    #     ├── xyxy            좌측 상단과 우측 하단 좌표
    #     ├── cls             클래스 번호
    #     └── conf            신뢰도

    # 원본 이미지 크기 가져오기
    image_height, image_width = result.orig_shape
    detections = []

    # 탐지된 가구별 좌표, 클래스 번호, 신뢰도 가져오기
    for xyxy, class_value, score in zip(
        result.boxes.xyxy.cpu().tolist(),
        result.boxes.cls.cpu().tolist(),
        result.boxes.conf.cpu().tolist(),
    ):
        # 박스의 너비와 높이 계산
        x1, y1, x2, y2 = xyxy
        width = max(0.0, x2 - x1)
        height = max(0.0, y2 - y1)
        class_id = int(class_value)

        # 탐지 결과를 응답 형식으로 만들기
        detections.append(
            {
                "class_id": class_id,
                "class_name": result.names.get(class_id, str(class_id)),
                "confidence": float(score),
                "bbox": {
                    # 픽셀 좌표를 0~1 범위로 변환
                    "x": float((x1 + width / 2) / image_width),
                    "y": float((y1 + height / 2) / image_height),
                    "width": float(width / image_width),
                    "height": float(height / image_height),
                },
            }
        )

    # 전체 탐지 결과 반환
    return detections
```

`device`는 YOLO 추론 계산에 사용할 장치를 의미한다. 로컬에서는 `cpu`를 사용하고, NVIDIA GPU를 사용할 수 있는 서버에서는 `cuda`로 설정한다. `model.to(device)`는 이후 추론을 지정한 CPU 또는 GPU에서 실행하도록 모델을 준비하는 코드다.

현재 Django 코드와 마찬가지로 bbox는 `0~1` 범위의 중심점 `x`, `y`와 크기 `width`, `height`를 반환한다.

`run_inference()`에는 `ThreadingLocked`를 적용한다. 일반 `def`로 선언한 FastAPI 엔드포인트는 스레드 풀에서 실행되므로 여러 요청이 동일한 YOLO 모델을 동시에 호출할 수 있다. 잠금은 공유 모델의 동시 접근을 막아 추론 요청을 한 번에 하나씩 안전하게 처리한다.

## 8. FastAPI 애플리케이션과 라우터 작성

API 경로를 `main.py`에 모두 작성하지 않고 기능별 라우터로 분리한다. FastAPI의 `APIRouter`는 관련 엔드포인트를 하나의 단위로 묶는 객체다. 각 라우터 파일에서 URL과 처리 함수를 정의하고, `main.py`는 애플리케이션 생성과 라우터 등록을 담당한다.

```text
FastAPI app
├── health router
│   └── GET /health
└── inference router
    └── POST /inferences
```

전체 애플리케이션과 라우터의 관계를 정리하면 다음과 같다.

- `app = FastAPI()` → 전체 애플리케이션을 만든다.
- `router = APIRouter()` → 기능별 API를 묶어 놓을 작은 라우터를 만든다.
- `app.include_router(router)` → 작은 라우터를 전체 애플리케이션에 연결한다.

라우터를 사용하지 않고 모든 API를 `main.py`의 `app` 객체에 직접 등록해도 동작한다. 하지만 API가 늘어나면 `main.py`가 길어지고 기능을 찾거나 수정하기 어려워진다. 따라서 `health`, `inference`처럼 기능별 라우터 파일로 나누고, `main.py`에서는 애플리케이션 생성과 라우터 연결만 담당하도록 구성한다.

### 8.1. 상태 확인 라우터

`/health`는 FastAPI가 API 요청에 응답할 수 있는 상태인지 확인한다. 이 애플리케이션은 서버 시작 시 YOLO 모델을 먼저 로딩하므로, 정상 응답하면 모델 로딩도 성공한 것으로 간접 확인할 수 있지만 실제 추론까지 검사하지는 않는다.

`app/routers/health.py`를 작성한다.

```python
# APIRouter: 기능별로 API 경로를 나누어 작성할 때 사용하는 클래스
from fastapi import APIRouter


# 이 파일에서 사용할 라우터 객체 생성
# tags는 Swagger 문서에서 API를 묶어 보여줄 이름이다.
router = APIRouter(tags=["health"])


# GET /health 요청이 들어오면 바로 아래 health 함수를 실행한다.
@router.get("/health")
def health():
    # Python 딕셔너리를 반환하면 FastAPI가 JSON 응답으로 변환한다.
    return {"status": "ok"}
```

`APIRouter`에 등록된 경로는 아직 FastAPI 애플리케이션에 연결된 상태가 아니다. 뒤에서 `app.include_router()`를 호출해야 실제 요청을 받을 수 있다.

### 8.2. 이미지 추론 라우터

`app/routers/inference.py`를 작성한다.

```python
# 업로드한 파일의 확장자를 다루기 위한 Python 표준 라이브러리
from pathlib import Path
# 요청 처리 후 임시 파일과 디렉터리를 자동으로 삭제하기 위한 표준 라이브러리
from tempfile import TemporaryDirectory

# APIRouter: API 경로 분리
# HTTPException: 오류 상태 코드와 메시지 반환
# Request: 현재 요청과 FastAPI 애플리케이션 정보 접근
# UploadFile: multipart/form-data로 업로드된 파일 받기
from fastapi import APIRouter, HTTPException, Request, UploadFile

# API 응답 형식을 정의한 Pydantic 모델
from app.schemas import InferenceResponse
# YOLO 추론을 수행하는 서비스 함수
from app.yolo_service import run_inference


# 업로드 가능한 최대 파일 크기: 10MB
MAX_FILE_SIZE = 10 * 1024 * 1024
# 허용할 이미지의 Content-Type 목록
ALLOWED_CONTENT_TYPES = {"image/jpeg", "image/png", "image/webp"}

# 이 파일에 정의하는 API의 공통 URL을 /inferences로 설정
router = APIRouter(prefix="/inferences", tags=["inferences"])


# POST /inferences 요청을 처리한다.
# 반환값은 InferenceResponse 형식인지 검증한 뒤 JSON으로 응답한다.
@router.post("", response_model=InferenceResponse)
def create_inference(request: Request, file: UploadFile):
    # 지원하지 않는 이미지 형식이면 더 진행하지 않고 415 오류를 반환한다.
    if file.content_type not in ALLOWED_CONTENT_TYPES:
        raise HTTPException(status_code=415, detail="Unsupported image type")

    # 최대 크기보다 1바이트 더 읽어 실제로 10MB를 초과하는지 확인한다.
    content = file.file.read(MAX_FILE_SIZE + 1)
    if len(content) > MAX_FILE_SIZE:
        raise HTTPException(status_code=413, detail="Image is too large")

    # 원본 파일명에서 .jpg, .png 같은 확장자를 가져온다.
    suffix = Path(file.filename or "upload.jpg").suffix.lower()

    # YOLO가 읽을 수 있도록 업로드 이미지를 임시 파일로 저장한다.
    # with 블록을 벗어나면 임시 디렉터리와 파일은 자동으로 삭제된다.
    with TemporaryDirectory() as temp_dir:
        image_path = Path(temp_dir) / f"input{suffix}"
        image_path.write_bytes(content)

        try:
            # 서버 시작 시 app.state에 저장한 모델과 설정으로 추론한다.
            detections = run_inference(
                request.app.state.model,
                image_path,
                request.app.state.image_size,
                request.app.state.confidence,
            )
        except Exception as exc:
            # 이미지 해석이나 YOLO 추론에 실패하면 422 오류를 반환한다.
            raise HTTPException(status_code=422, detail="Image inference failed") from exc

    # Python 딕셔너리를 반환한다.
    # FastAPI가 InferenceResponse로 검증하고 최종 JSON 응답으로 변환한다.
    return {
        "model_version": request.app.state.model_version,
        "detections": detections,
    }
```

`prefix="/inferences"`를 지정했으므로 함수의 경로는 빈 문자열로 작성해도 최종 경로는 `POST /inferences`가 된다. `tags`는 Swagger 문서에서 API를 기능별로 묶어 표시한다.

라우터는 `main.py`의 전역 변수에 직접 의존하지 않는다. 현재 요청의 `Request` 객체를 통해 동일한 FastAPI 애플리케이션의 `app.state`에 저장된 모델과 설정을 가져온다. 이 방식은 `main.py`와 라우터 사이의 순환 import도 방지한다.

#### FastAPI 문법 정리

- `request: Request`, `file: UploadFile`처럼 `변수명: 타입`으로 작성하는 것은 Python의 **타입 힌트**다. FastAPI는 타입 힌트를 읽고 HTTP 요청에서 필요한 값을 자동으로 만들어 함수에 전달한다.
- `file: UploadFile`을 선언하면 FastAPI는 `multipart/form-data` 요청에서 `file`이라는 이름의 업로드 파일을 찾는다. Spring Boot의 `MultipartFile`과 비슷하다.
- `response_model=InferenceResponse`는 응답 DTO를 지정하는 것과 비슷하다. 함수가 딕셔너리를 반환해도 FastAPI와 Pydantic이 응답 구조와 값의 타입을 검사한다.
- `raise HTTPException(...)`은 일반 값을 반환하는 대신 즉시 오류 응답을 만드는 FastAPI 방식이다. `status_code`는 HTTP 상태 코드이고 `detail`은 응답에 담을 오류 내용이다.
- `request.app`은 현재 요청을 처리하는 FastAPI 객체다. `request.app.state`에는 애플리케이션 전체에서 함께 사용할 값을 저장할 수 있다.
- `with TemporaryDirectory()`는 Python의 **컨텍스트 매니저** 문법이다. 들여쓰기된 작업이 끝나거나 중간에 오류가 발생해도 임시 디렉터리를 정리한다.
- `except Exception as exc`에서 잡은 원래 오류를 `from exc`로 연결하면, 클라이언트에는 정리된 메시지를 주면서 서버 로그에서는 실제 원인을 추적할 수 있다.

요청 한 건은 다음 순서로 처리된다.

```text
Spring Boot가 이미지 전송
→ FastAPI가 UploadFile로 파일 수신
→ 이미지 형식과 크기 검사
→ 임시 파일 생성
→ app.state의 YOLO 모델로 추론
→ 임시 파일 자동 삭제
→ Pydantic 응답 형식 검사
→ JSON 응답 반환
```

### 8.3. 애플리케이션 생성과 라우터 등록

로컬 설정을 한곳에서 관리하기 위해 `yolo/.env`를 작성한다.

```dotenv
YOLO_MODEL_VERSION=best_260204
YOLO_DEVICE=cpu
YOLO_IMAGE_SIZE=640
YOLO_CONFIDENCE=0.4
```

모델이 기본 위치인 `yolo/weights/`에 없다면 `YOLO_MODEL_PATH`도 추가한다. `.env` 값은 빈 문자열로 두지 않고, 기본값을 사용할 변수는 작성하지 않는다. `.env`는 Git에 올리지 않는다.

`app/main.py`를 작성한다.

```python
# 운영체제 환경 변수를 읽기 위한 Python 표준 라이브러리
import os
# lifespan 함수를 비동기 컨텍스트 매니저로 만들기 위한 표준 라이브러리
from contextlib import asynccontextmanager
# 운영체제와 관계없이 파일 경로를 다루기 위한 표준 라이브러리
from pathlib import Path

# .env 파일의 값을 환경 변수로 불러오는 함수
from dotenv import load_dotenv
# FastAPI 애플리케이션 객체를 만드는 클래스
from fastapi import FastAPI

# 각 파일의 router 변수는 이름이 같으므로 별칭을 붙여 구분한다.
from app.routers.health import router as health_router
from app.routers.inference import router as inference_router
# YOLO 모델을 불러오는 서비스 함수
from app.yolo_service import load_model


# 로컬 .env 파일을 찾아 설정값을 환경 변수로 불러온다.
load_dotenv()

# 현재 파일의 두 단계 위인 yolo/ 디렉터리 경로
BASE_DIR = Path(__file__).resolve().parent.parent
# 환경 변수가 있으면 그 값을 사용하고, 없으면 두 번째 인자의 기본값을 사용한다.
MODEL_PATH = Path(os.getenv("YOLO_MODEL_PATH", BASE_DIR / "weights" / "best_260204.pt"))
MODEL_VERSION = os.getenv("YOLO_MODEL_VERSION", "best_260204")
DEVICE = os.getenv("YOLO_DEVICE", "cpu")
IMAGE_SIZE = int(os.getenv("YOLO_IMAGE_SIZE", "640"))
CONFIDENCE = float(os.getenv("YOLO_CONFIDENCE", "0.4"))


# FastAPI 서버가 시작하고 종료될 때 실행할 생명주기 함수
@asynccontextmanager
async def lifespan(app: FastAPI):
    # yield 전: 서버 시작 시 한 번 실행
    # 모델과 설정을 app.state에 저장해 모든 요청에서 함께 사용한다.
    app.state.model = load_model(MODEL_PATH, DEVICE)
    app.state.model_version = MODEL_VERSION
    app.state.image_size = IMAGE_SIZE
    app.state.confidence = CONFIDENCE

    try:
        # 여기서 서버가 요청을 받는다.
        # 서버가 종료될 때까지 lifespan 실행이 이 지점에서 유지된다.
        yield
    finally:
        # yield 후: 서버 종료 시 실행
        # 애플리케이션이 가지고 있던 모델 참조를 해제한다.
        app.state.model = None


# FastAPI 핵심 애플리케이션 객체 생성
# lifespan 인자로 위의 시작·종료 함수를 등록한다.
app = FastAPI(title="Zimpic YOLO Inference API", lifespan=lifespan)

# 분리해 둔 라우터의 API 경로를 실제 애플리케이션에 등록한다.
app.include_router(health_router)
app.include_router(inference_router)
```

`app.include_router()`는 각 `APIRouter`에 정의한 엔드포인트를 FastAPI 애플리케이션에 연결한다. 파일을 import하는 것만으로는 라우터가 등록되지 않으므로 두 라우터를 모두 명시적으로 포함해야 한다.

`lifespan`은 FastAPI 서버가 시작될 때 YOLO 모델을 한 번 로딩하고 서버가 종료될 때 참조를 해제한다. 요청마다 모델을 다시 생성하지 않고 `app.state.model`에 저장한 인스턴스를 재사용한다. 이는 비용이 큰 공유 자원을 애플리케이션 생명주기에 맞춰 초기화하고 정리하는 방식이다.

#### Python과 FastAPI 문법 정리

- `from ... import router as health_router`의 `as`는 가져온 변수에 이 파일에서 사용할 별명을 붙인다. 두 라우터 파일 모두 변수 이름이 `router`이므로 별칭으로 구분한다.
- `load_dotenv()`는 로컬 `.env` 값을 현재 Python 프로세스의 환경 변수로 불러온다. Docker에서는 컨테이너에 전달한 환경 변수를 같은 `os.getenv()` 코드로 읽을 수 있다.
- `__file__`은 현재 실행 중인 Python 파일의 경로다. `resolve()`로 절대 경로를 구하고, `.parent.parent`로 `app/main.py`에서 두 단계 위인 `yolo/`를 찾는다.
- `os.getenv("이름", "기본값")`은 운영체제 환경 변수를 읽는다. 환경 변수가 없으면 기본값을 사용하므로 소스를 수정하지 않고도 Docker나 서버에서 설정을 바꿀 수 있다.
- 환경 변수는 문자열로 들어온다. 그래서 이미지 크기는 `int(...)`, 신뢰도는 `float(...)`로 숫자 타입을 변환한다.
- `async def`는 비동기 함수를 선언하는 Python 문법이다. 여기서는 FastAPI가 요구하는 lifespan 형식에 맞추기 위해 사용한다. YOLO 추론 함수까지 무조건 `async def`로 바꿔야 한다는 뜻은 아니다.
- `yield`를 기준으로 앞부분은 서버 시작 시, 뒷부분은 서버 종료 시 실행된다. `try/finally`를 사용하면 서버가 종료되는 과정에서도 정리 코드가 실행된다.
- `app.state`는 FastAPI가 자유롭게 공유 값을 보관하도록 제공하는 공간이다. 미리 정해진 `model` 필드가 있는 것이 아니라 우리가 `app.state.model`이라는 이름으로 직접 저장한 것이다.

모델은 프로세스당 하나씩 생성된다. Uvicorn worker나 Docker 컨테이너를 여러 개 실행하면 각 프로세스 또는 컨테이너가 별도의 모델 인스턴스를 갖는다.

위 코드는 첫 실행을 위한 최소 형태다. 이후 실제 이미지 형식과 픽셀 크기를 Pillow로 검증하고, 잘못된 이미지와 모델 오류를 구분하도록 보완한다.

## 9. 로그 추가

로컬 터미널과 Docker 로그에서 모델 로딩, 추론 요청, 성공과 실패를 확인할 수 있도록 Python `logging`을 사용한다. Docker는 특정 이름의 로거를 찾는 것이 아니라 프로세스가 콘솔에 출력한 로그를 수집한다.

### 9.1. 모델 로딩과 종료 로그

`app/main.py` 상단에 다음 코드를 추가한다.

```python
import logging


# INFO 이상의 로그를 콘솔에 출력하고 시간, 수준, 발생 위치를 함께 표시한다.
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s: %(message)s",
)

# __name__은 현재 파일의 모듈 이름이다. main.py에서는 app.main이 된다.
logger = logging.getLogger(__name__)
```

`lifespan`에 모델 로딩 결과와 서버 종료 로그를 추가한다.

```python
@asynccontextmanager
async def lifespan(app: FastAPI):
    try:
        logger.info("YOLO 모델 로딩 시작: path=%s, device=%s", MODEL_PATH, DEVICE)

        app.state.model = load_model(MODEL_PATH, DEVICE)
        app.state.model_version = MODEL_VERSION
        app.state.image_size = IMAGE_SIZE
        app.state.confidence = CONFIDENCE

        logger.info("YOLO 모델 로딩 완료: version=%s", MODEL_VERSION)
    except Exception:
        # logger.exception은 메시지와 실제 오류 위치를 함께 기록한다.
        logger.exception("YOLO 모델 로딩 실패")
        raise

    try:
        yield
    finally:
        logger.info("YOLO 서버 종료")
        app.state.model = None
```

모델 로딩에 실패하면 `raise`로 원래 예외를 다시 발생시켜, 준비되지 않은 상태로 서버가 실행되는 것을 막는다.

### 9.2. 추론 요청 로그

`app/routers/inference.py` 상단에도 로거를 추가한다.

```python
import logging


# inference.py에서는 로거 이름이 app.routers.inference가 된다.
logger = logging.getLogger(__name__)
```

로그 출력 설정은 `main.py`에서 한 번만 하고, 각 파일에서는 `logging.getLogger(__name__)`으로 자신의 로거를 가져와 사용한다.

`create_inference()`가 호출되면 먼저 API 요청 수신 로그를 남긴다. 이 로그는 실제 YOLO 추론을 시작했다는 뜻이 아니라, 이미지 검증 전 요청이 들어왔다는 뜻이다.

```python
logger.info(
    "추론 요청 수신: content_type=%s",
    file.content_type,
)
```

이미지 형식과 크기 검사를 통과하고 임시 파일을 만든 다음, `run_inference()` 바로 앞에 실제 YOLO 추론 시작 로그를 남긴다. 이어서 성공 또는 실패 로그를 기록한다.

```python
try:
    logger.info("YOLO 추론 시작: size=%d", len(content))

    detections = run_inference(
        request.app.state.model,
        image_path,
        request.app.state.image_size,
        request.app.state.confidence,
    )

    logger.info("YOLO 추론 완료: detections=%d", len(detections))
except Exception as exc:
    logger.exception("YOLO 추론 실패")
    raise HTTPException(
        status_code=422,
        detail="이미지 추론 실패",
    ) from exc
```

이미지 내용이나 개인정보는 로그에 남기지 않는다. Docker 적용 후에는 `docker compose logs -f yolo-inference`로 같은 로그를 실시간 확인할 수 있다.

## 10. 최종 애플리케이션 로컬 실행

`yolo/` 위치에서 가상환경이 활성화된 상태로 실행한다.

```powershell
fastapi dev app/main.py
```

`fastapi`와 YOLO 관련 라이브러리는 `.venv`에 설치했으므로 서버 실행에는 가상환경이 필요하다. 서버 로그가 계속 표시되어야 하므로 이 터미널은 종료하지 않는다.

`fastapi dev`는 개발용 서버를 실행하는 명령이다. `app/main.py`를 읽어 그 안의 `app = FastAPI(...)` 객체를 찾아 실행한다. 개발 중에는 파일 변경을 감지해 서버를 자동으로 다시 시작한다. 운영 환경에서는 이후 Docker 단계에서 개발용 명령이 아닌 운영용 실행 방법을 별도로 정한다.

기본 주소는 다음과 같다.

```text
API:     http://127.0.0.1:8000
Swagger: http://127.0.0.1:8000/docs
```

Swagger 화면에서는 별도의 API 테스트 프로그램 없이 파일을 선택해 `/inferences`를 호출할 수 있다.

## 11. 기본 동작 확인

10번의 서버를 실행해 둔 상태에서 두 번째 PowerShell 터미널을 연다. `curl.exe`는 Windows 명령이므로 가상환경 활성화와 현재 디렉터리는 상관없다.

두 번째 터미널에서 서버 상태를 확인한다.

```powershell
curl.exe http://127.0.0.1:8000/health
```

예상 응답:

```json
{"status":"ok"}
```

같은 두 번째 터미널에서 이미지 추론을 요청한다. 이미지에는 실제 파일의 절대 경로를 사용한다.

```powershell
curl.exe -X POST "http://127.0.0.1:8000/inferences" -F "file=@C:\path\to\room.jpg"
```

- `-X POST`는 POST 방식으로 요청한다는 뜻이다.
- `-F`는 파일 업로드에 사용하는 `multipart/form-data` 형식으로 요청을 만든다.
- `file=`은 `create_inference()`의 매개변수 이름 `file`과 같아야 한다.
- `@C:\path\to\room.jpg`의 `@`는 문자열이 아니라 해당 경로의 실제 파일을 전송하라는 curl 문법이다.

예상 응답 형태:

```json
{
  "model_version": "best_260204",
  "detections": [
    {
      "class_id": 26,
      "class_name": "sofa_lg",
      "confidence": 0.9,
      "bbox": {
        "x": 0.48,
        "y": 0.75,
        "width": 0.31,
        "height": 0.22
      }
    }
  ]
}
```

정상 실행 시 서버를 실행한 첫 번째 터미널에는 다음과 같은 로그가 출력된다.

```text
2026-10-15 15:45:18,984 INFO app.main: YOLO 모델 로딩 시작: path=C:\...\yolo\weights\best_260204.pt, device=cpu
2026-10-15 15:45:19,095 INFO app.main: YOLO 모델 로딩 완료: version=best_260204
Application startup complete.
127.0.0.1:58306 - "GET /health HTTP/1.1" 200
2026-10-15 15:50:39,723 INFO app.routers.inference: 추론 요청 수신: content_type=image/jpeg
2026-10-15 15:50:39,724 INFO app.routers.inference: YOLO 추론 시작
2026-10-15 15:50:42,547 INFO app.routers.inference: YOLO 추론 완료: detections=3
127.0.0.1:59608 - "POST /inferences HTTP/1.1" 200
```

시간, 클라이언트 포트와 탐지 개수는 실행할 때마다 달라질 수 있다. 마지막의 `200`은 요청이 정상 처리되었다는 뜻이다.

탐지된 물체가 없을 때는 오류가 아니라 빈 배열을 반환한다.

```json
{
  "model_version": "best_260204",
  "detections": []
}
```

## 12. 간단한 테스트 작성

`tests/test_health.py`를 작성한다.

```python
# FastAPI 애플리케이션을 실제 서버 없이 테스트하는 도구
from fastapi.testclient import TestClient

# main.py에서 생성한 FastAPI app 객체
from app.main import app


def test_health():
    # TestClient를 시작하면 lifespan도 함께 실행된다.
    # with 블록이 끝나면 클라이언트와 lifespan을 정상 종료한다.
    with TestClient(app) as client:
        # 테스트용으로 GET /health 요청 보내기
        response = client.get("/health")

    # HTTP 요청이 성공했는지 확인
    assert response.status_code == 200
    # JSON 응답 내용이 예상값과 같은지 확인
    assert response.json() == {"status": "ok"}
```

`pytest`는 이름이 `test_`로 시작하는 파일과 함수를 자동으로 찾아 실행한다. `assert` 뒤의 조건이 참이면 통과하고 거짓이면 테스트가 실패한다. `TestClient`는 네트워크 포트에 실제 서버를 따로 띄우지 않고도 FastAPI 요청과 응답을 검사할 수 있게 해준다.

`pytest`는 프로젝트 라이브러리를 사용하므로 `yolo/` 위치에서 가상환경을 활성화한 뒤 실행한다. `TestClient`가 테스트용 애플리케이션을 직접 실행하므로 10번의 개발 서버는 켜져 있지 않아도 된다.

```powershell
cd yolo
.\.venv\Scripts\Activate.ps1
pytest
```

현재 구조에서는 테스트 시작 시 실제 YOLO 모델도 로딩된다. 이후 테스트가 느려지면 테스트용 가짜 모델을 주입하도록 구조를 개선한다.

## 13. 기존 Django 결과와 비교

동일한 이미지와 설정을 사용해 다음 항목을 비교한다.

- 클래스 ID
- 클래스 이름
- confidence
- bbox 좌표
- 탐지 개수

다음 설정이 기존과 동일해야 한다.

```text
model:     best_260204.pt
image size: 640
confidence: 0.4
device:     cpu 또는 동일한 GPU 환경
```

FastAPI는 Furniture 정보를 조회하지 않으므로 가구 ID, 한글 이름, 기본 크기는 반환하지 않는 것이 정상이다. 이 정보는 이후 Spring Boot가 `class_id`를 기준으로 PostgreSQL에서 조회한다.

## 완료 기준

- FastAPI를 Docker 없이 로컬에서 실행할 수 있다.
- 서버 시작 시 YOLO 모델이 한 번만 로딩된다.
- `/health`가 정상 응답한다.
- `/inferences`에 이미지 한 장을 업로드할 수 있다.
- 탐지 결과가 정해진 JSON 형식으로 반환된다.
- 탐지 결과가 없으면 빈 배열을 반환한다.
- FastAPI가 DB와 Furniture 모델에 의존하지 않는다.
- 동일한 이미지에서 기존 Django 추론과 핵심 결과가 일치한다.

## 참고 문서

- [Windows에서 Python 사용](https://docs.python.org/3/using/windows.html)
- [Homebrew Python 3.11](https://formulae.brew.sh/formula/python@3.11)
- [FastAPI 첫 단계](https://fastapi.tiangolo.com/tutorial/first-steps/)
- [FastAPI 파일 업로드](https://fastapi.tiangolo.com/tutorial/request-files/)
- [FastAPI Lifespan 이벤트](https://fastapi.tiangolo.com/advanced/events/)
- [FastAPI 테스트](https://fastapi.tiangolo.com/tutorial/testing/)

