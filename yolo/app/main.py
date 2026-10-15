import os
import logging
from contextlib import asynccontextmanager
from pathlib import Path
from dotenv import load_dotenv
from fastapi import FastAPI

from app.routers.health import router as health_router
from app.routers.inference import router as inference_router
from app.yolo_service import load_model


# 실행 테스트
# app = FastAPI()

# @app.get("/hello")
# def hello():
#     return {"message": "hello"}


########## 로그출력 설정 ##########
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s: %(message)s"
)
logger = logging.getLogger(__name__)



########## 변수 설정 ##########
load_dotenv()

# 현재 파일 위치(__file__)를 절대경로로 변경(resolve())한 뒤 상위 상위 경로
BASE_DIR = Path(__file__).resolve().parent.parent
# YOLO_MODEL_PATH 변수값 있으면 사용, 없으면 BASE_DIR조합한 모델 경로 사용 
MODEL_PATH = Path(os.getenv("YOLO_MODEL_PATH", BASE_DIR / "weights" / "best_260204.pt"))

MODEL_VERSION = os.getenv("YOLO_MODEL_VERSION", "best_260204")
DEVICE = os.getenv("YOLO_DEVICE", "cpu")
IMAGE_SIZE = int(os.getenv("YOLO_IMAGE_SIZE"), 640)
CONFIDENCE = float(os.getenv("YOLO_CONFIDENCE"), 0.4)



########## 생명주기 함수 ##########
# YOLO모델을 서버가 시작할 때 한번 로딩하고, 서버가 종료될 때 참조 해제함
# -> 요청마다 다시 생성하지 않고 app.state.model에 저장한 인스턴스를 재사용함
@asynccontextmanager
async def lifespan(app: FastAPI):

    # 서버 시작 시 모델 로딩 실행
    try:
        logger.info("YOLO 모델 로딩 시작: path=%s, device=%s", MODEL_PATH, DEVICE)

        app.state.model = load_model(MODEL_PATH, DEVICE)
        app.state.model_version = MODEL_VERSION
        app.state.image_size = IMAGE_SIZE
        app.state.confidence = CONFIDENCE

        logger.info("YOLO 모델 로딩 완료: version=%s", MODEL_VERSION)

    except Exception:
        logger.exception("YOLO 모델 로딩 실패")
        raise


    try:
        yield
    finally:
        logger.info("YOLO 서버 종료")
        # 서버 종료 시 모델 비우기
        app.state.model = None



########## 라우터 경로 조합 ##########
app = FastAPI(title="YOLO API", lifespan=lifespan)
app.include_router(health_router)
app.include_router(inference_router)