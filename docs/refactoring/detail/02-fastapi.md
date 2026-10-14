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
ultralytics
pillow
pytest
httpx
```

설치한다.

```powershell
pip install -r requirements.txt
```

`python-multipart`는 `multipart/form-data` 형식의 이미지 업로드를 받는 데 필요하다. 이미지 전체를 한 번에 `bytes`로 받기보다 FastAPI의 `UploadFile`을 사용한다.

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

### 8.1. 상태 확인 라우터

`app/routers/health.py`를 작성한다.

```python
from fastapi import APIRouter


router = APIRouter(tags=["health"])


@router.get("/health")
def health():
    return {"status": "ok"}
```

`APIRouter`에 등록된 경로는 아직 FastAPI 애플리케이션에 연결된 상태가 아니다. 뒤에서 `app.include_router()`를 호출해야 실제 요청을 받을 수 있다.

### 8.2. 이미지 추론 라우터

`app/routers/inference.py`를 작성한다.

```python
from pathlib import Path
from tempfile import TemporaryDirectory

from fastapi import APIRouter, HTTPException, Request, UploadFile

from app.schemas import InferenceResponse
from app.yolo_service import run_inference


MAX_FILE_SIZE = 10 * 1024 * 1024
ALLOWED_CONTENT_TYPES = {"image/jpeg", "image/png", "image/webp"}

router = APIRouter(prefix="/inferences", tags=["inferences"])


@router.post("", response_model=InferenceResponse)
def create_inference(request: Request, file: UploadFile):
    if file.content_type not in ALLOWED_CONTENT_TYPES:
        raise HTTPException(status_code=415, detail="Unsupported image type")

    content = file.file.read(MAX_FILE_SIZE + 1)
    if len(content) > MAX_FILE_SIZE:
        raise HTTPException(status_code=413, detail="Image is too large")

    suffix = Path(file.filename or "upload.jpg").suffix.lower()

    with TemporaryDirectory() as temp_dir:
        image_path = Path(temp_dir) / f"input{suffix}"
        image_path.write_bytes(content)

        try:
            detections = run_inference(
                request.app.state.model,
                image_path,
                request.app.state.image_size,
                request.app.state.confidence,
            )
        except Exception as exc:
            raise HTTPException(status_code=422, detail="Image inference failed") from exc

    return {
        "model_version": request.app.state.model_version,
        "detections": detections,
    }
```

`prefix="/inferences"`를 지정했으므로 함수의 경로는 빈 문자열로 작성해도 최종 경로는 `POST /inferences`가 된다. `tags`는 Swagger 문서에서 API를 기능별로 묶어 표시한다.

라우터는 `main.py`의 전역 변수에 직접 의존하지 않는다. 현재 요청의 `Request` 객체를 통해 동일한 FastAPI 애플리케이션의 `app.state`에 저장된 모델과 설정을 가져온다. 이 방식은 `main.py`와 라우터 사이의 순환 import도 방지한다.

### 8.3. 애플리케이션 생성과 라우터 등록

`app/main.py`를 작성한다.

```python
import os
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI

from app.routers.health import router as health_router
from app.routers.inference import router as inference_router
from app.yolo_service import load_model


BASE_DIR = Path(__file__).resolve().parent.parent
MODEL_PATH = Path(os.getenv("YOLO_MODEL_PATH", BASE_DIR / "weights" / "best_260204.pt"))
MODEL_VERSION = os.getenv("YOLO_MODEL_VERSION", "best_260204")
DEVICE = os.getenv("YOLO_DEVICE", "cpu")
IMAGE_SIZE = int(os.getenv("YOLO_IMAGE_SIZE", "640"))
CONFIDENCE = float(os.getenv("YOLO_CONFIDENCE", "0.4"))


@asynccontextmanager
async def lifespan(app: FastAPI):
    app.state.model = load_model(MODEL_PATH, DEVICE)
    app.state.model_version = MODEL_VERSION
    app.state.image_size = IMAGE_SIZE
    app.state.confidence = CONFIDENCE

    try:
        yield
    finally:
        app.state.model = None


app = FastAPI(title="Zimpic YOLO Inference API", lifespan=lifespan)

app.include_router(health_router)
app.include_router(inference_router)
```

`app.include_router()`는 각 `APIRouter`에 정의한 엔드포인트를 FastAPI 애플리케이션에 연결한다. 파일을 import하는 것만으로는 라우터가 등록되지 않으므로 두 라우터를 모두 명시적으로 포함해야 한다.

`lifespan`은 FastAPI 서버가 시작될 때 YOLO 모델을 한 번 로딩하고 서버가 종료될 때 참조를 해제한다. 요청마다 모델을 다시 생성하지 않고 `app.state.model`에 저장한 인스턴스를 재사용한다. 이는 비용이 큰 공유 자원을 애플리케이션 생명주기에 맞춰 초기화하고 정리하는 방식이다.

모델은 프로세스당 하나씩 생성된다. Uvicorn worker나 Docker 컨테이너를 여러 개 실행하면 각 프로세스 또는 컨테이너가 별도의 모델 인스턴스를 갖는다.

위 코드는 첫 실행을 위한 최소 형태다. 이후 실제 이미지 형식과 픽셀 크기를 Pillow로 검증하고, 잘못된 이미지와 모델 오류를 구분하도록 보완한다.

## 9. 최종 애플리케이션 로컬 실행

`yolo/` 위치에서 실행한다.

```powershell
fastapi dev app/main.py
```

기본 주소는 다음과 같다.

```text
API:     http://127.0.0.1:8000
Swagger: http://127.0.0.1:8000/docs
```

Swagger 화면에서는 별도의 API 테스트 프로그램 없이 파일을 선택해 `/inferences`를 호출할 수 있다.

## 10. 기본 동작 확인

서버 상태를 확인한다.

```powershell
curl.exe http://127.0.0.1:8000/health
```

예상 응답:

```json
{"status":"ok"}
```

이미지 추론을 요청한다.

```powershell
curl.exe -X POST "http://127.0.0.1:8000/inferences" -F "file=@C:\path\to\room.jpg"
```

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

탐지된 물체가 없을 때는 오류가 아니라 빈 배열을 반환한다.

```json
{
  "model_version": "best_260204",
  "detections": []
}
```

## 11. 간단한 테스트 작성

`tests/test_health.py`를 작성한다.

```python
from fastapi.testclient import TestClient

from app.main import app


def test_health():
    with TestClient(app) as client:
        response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}
```

실행한다.

```powershell
pytest
```

현재 구조에서는 테스트 시작 시 실제 YOLO 모델도 로딩된다. 이후 테스트가 느려지면 테스트용 가짜 모델을 주입하도록 구조를 개선한다.

## 12. 기존 Django 결과와 비교

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

