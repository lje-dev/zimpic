# FastAPI YOLO 추론 서버

이미지 한 장을 받아 YOLO로 가구를 탐지하고 JSON 결과를 반환한다.

## 실행 환경

- Python 3.11
- FastAPI
- Ultralytics YOLO

모델 파일은 기본적으로 `weights/best_260204.pt`에 둔다.

## 최초 설치

`yolo/` 디렉터리에서 실행한다.

### Windows PowerShell

```powershell
py -3.11 -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
```

### macOS

```bash
python3.11 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

## 환경 변수

`yolo/.env`를 작성한다.

```dotenv
YOLO_MODEL_VERSION=best_260204
YOLO_DEVICE=cpu
YOLO_IMAGE_SIZE=640
YOLO_CONFIDENCE=0.4
```

모델이 기본 위치에 없다면 `YOLO_MODEL_PATH`에 경로를 지정한다. `.env`는 Git에 올리지 않는다.

## 서버 실행

`yolo/` 위치에서 가상환경이 활성화된 상태로 실행한다.

```powershell
fastapi dev app/main.py
```

- API: <http://127.0.0.1:8000>
- Swagger: <http://127.0.0.1:8000/docs>
- 상태 확인: <http://127.0.0.1:8000/health>

## 테스트

`yolo/` 위치에서 가상환경이 활성화된 상태로 실행한다. 개발 서버는 켜져 있지 않아도 된다.

```powershell
python -m pytest
```

## API

- `GET /health`: FastAPI 서버 상태 확인
- `POST /inferences`: 이미지 한 장 업로드 및 YOLO 추론

자세한 구현 과정은 [FastAPI YOLO 서버 분리 가이드](../docs/refactoring/detail/02-fastapi.md)를 참고한다.
