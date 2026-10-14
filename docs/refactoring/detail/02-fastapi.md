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
yolo-service/
├── app/
│   ├── __init__.py
│   ├── main.py
│   ├── schemas.py
│   └── inference.py
├── tests/
│   └── test_health.py
├── weights/
│   └── best_260204.pt
└── requirements.txt
```

기존 `backend/`는 비교를 위해 그대로 유지한다. 모델 파일도 기존 파일을 삭제하지 않고 `yolo-service/weights/`에 복사한다.

## 2. Python 가상환경 생성

PowerShell에서 다음 명령을 실행한다.

```powershell
cd yolo-service
py -3.11 -m venv .venv
.\.venv\Scripts\Activate.ps1
```

가상환경이 활성화되면 터미널 앞에 `(.venv)`가 표시된다.

## 3. 라이브러리 정의 및 설치

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

## 4. 응답 형식 정의

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

## 5. YOLO 추론 코드 분리

`app/inference.py`에는 Django 설정과 DB 코드 없이 모델 추론만 작성한다.

```python
from pathlib import Path

from ultralytics import YOLO


def load_model(model_path: Path, device: str):
    model = YOLO(str(model_path))
    model.to(device)
    return model


def run_inference(model, image_path: Path, image_size: int, confidence: float):
    results = model.predict(
        source=str(image_path),
        imgsz=image_size,
        conf=confidence,
        save=False,
        verbose=False,
    )

    if not results or results[0].boxes is None:
        return []

    result = results[0]
    image_height, image_width = result.orig_shape
    detections = []

    for xyxy, class_value, score in zip(
        result.boxes.xyxy.cpu().tolist(),
        result.boxes.cls.cpu().tolist(),
        result.boxes.conf.cpu().tolist(),
    ):
        x1, y1, x2, y2 = xyxy
        width = max(0.0, x2 - x1)
        height = max(0.0, y2 - y1)
        class_id = int(class_value)

        detections.append(
            {
                "class_id": class_id,
                "class_name": result.names.get(class_id, str(class_id)),
                "confidence": float(score),
                "bbox": {
                    "x": float((x1 + width / 2) / image_width),
                    "y": float((y1 + height / 2) / image_height),
                    "width": float(width / image_width),
                    "height": float(height / image_height),
                },
            }
        )

    return detections
```

현재 Django 코드와 마찬가지로 bbox는 `0~1` 범위의 중심점 `x`, `y`와 크기 `width`, `height`를 반환한다.

## 6. FastAPI 애플리케이션 작성

`app/main.py`를 작성한다.

```python
import os
from contextlib import asynccontextmanager
from pathlib import Path
from tempfile import TemporaryDirectory

from fastapi import FastAPI, HTTPException, UploadFile

from app.inference import load_model, run_inference
from app.schemas import InferenceResponse


BASE_DIR = Path(__file__).resolve().parent.parent
MODEL_PATH = Path(os.getenv("YOLO_MODEL_PATH", BASE_DIR / "weights" / "best_260204.pt"))
MODEL_VERSION = os.getenv("YOLO_MODEL_VERSION", "best_260204")
DEVICE = os.getenv("YOLO_DEVICE", "cpu")
IMAGE_SIZE = int(os.getenv("YOLO_IMAGE_SIZE", "640"))
CONFIDENCE = float(os.getenv("YOLO_CONFIDENCE", "0.4"))
MAX_FILE_SIZE = 10 * 1024 * 1024
ALLOWED_CONTENT_TYPES = {"image/jpeg", "image/png", "image/webp"}


@asynccontextmanager
async def lifespan(app: FastAPI):
    app.state.model = load_model(MODEL_PATH, DEVICE)
    yield
    app.state.model = None


app = FastAPI(title="Zimpic YOLO Inference API", lifespan=lifespan)


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/inferences", response_model=InferenceResponse)
def create_inference(file: UploadFile):
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
                app.state.model,
                image_path,
                IMAGE_SIZE,
                CONFIDENCE,
            )
        except Exception as exc:
            raise HTTPException(status_code=422, detail="Image inference failed") from exc

    return {
        "model_version": MODEL_VERSION,
        "detections": detections,
    }
```

`lifespan`은 FastAPI 서버가 시작될 때 YOLO 모델을 한 번 로딩한다. 요청마다 모델을 다시 생성하지 않는다.

위 코드는 첫 실행을 위한 최소 형태다. 이후 실제 이미지 형식과 픽셀 크기를 Pillow로 검증하고, 잘못된 이미지와 모델 오류를 구분하도록 보완한다.

## 7. 로컬 실행

`yolo-service/` 위치에서 실행한다.

```powershell
fastapi dev app/main.py
```

기본 주소는 다음과 같다.

```text
API:     http://127.0.0.1:8000
Swagger: http://127.0.0.1:8000/docs
```

Swagger 화면에서는 별도의 API 테스트 프로그램 없이 파일을 선택해 `/inferences`를 호출할 수 있다.

## 8. 기본 동작 확인

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

## 9. 간단한 테스트 작성

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

## 10. 기존 Django 결과와 비교

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

- [FastAPI 첫 단계](https://fastapi.tiangolo.com/tutorial/first-steps/)
- [FastAPI 파일 업로드](https://fastapi.tiangolo.com/tutorial/request-files/)
- [FastAPI Lifespan 이벤트](https://fastapi.tiangolo.com/advanced/events/)
- [FastAPI 테스트](https://fastapi.tiangolo.com/tutorial/testing/)

