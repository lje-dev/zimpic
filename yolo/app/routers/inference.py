import logging
from pathlib import Path

from tempfile import TemporaryDirectory # 임시파일 자동 삭제 라이브러리
from fastapi import APIRouter, HTTPException, Request, UploadFile

# 응답 형식 및 모델추론 함수 가져오기
from app.schemas import InferenceResponse
from app.yolo_service import run_inference


logger = logging.getLogger(__name__)


# 업로드 가능한 최대 파일 크기
MAX_FILE_SIZE = 10 * 1024 * 1024
# 허용 이미지 타입
ALLOWED_CONTENT_TYPES = {"image/jpeg", "image/png", "image/webp"}


router = APIRouter(prefix="/inferences", tags=["inferences"])


@router.post("", response_model=InferenceResponse)
def create_inference(request: Request, file: UploadFile):
    logger.info("추론 요청 수신: content_type=%s", file.content_type)
    
    # 1. 파일 형식 검사
    # 2. 파일 크기 검사
    # 3. 임시 파일 생성
    # 4. YOLO 서비스 호출
    # 5. 오류 변환
    # 6. 응답 생성


    # 1, 2 검사 및 예외처리
    if file.content_type not in ALLOWED_CONTENT_TYPES:
        raise HTTPException(
            status_code=415,
            detail="지원하지 않는 파일 형식"
        )

    content = file.file.read(MAX_FILE_SIZE + 1)
    if len(content) > MAX_FILE_SIZE :
        raise HTTPException(
            status_code=413, 
            detail="업로드 파일 용량 제한"
        )


    # 3. 임시파일 생성
    suffix = Path(file.filename or "upload.jpg").suffix.lower()

    with TemporaryDirectory() as temp_dir:
        image_path = Path(temp_dir) / f"input{suffix}"
        image_path.write_bytes(content)

        # 4. YOLO 호출
        logger.info("YOLO 추론 시작")
        try:
            detections = run_inference(
                request.app.state.model,
                image_path,
                request.app.state.image_size,
                request.app.state.confidence,
            )
            logger.info("YOLO 추론 완료: detections=%d", len(detections))
        except Exception as exc:
            # 5. 오류 변환
            logger.exception("YOLO 추론 실패")
            raise HTTPException(
                status_code=422,
                detail="이미지 추론 실패"
            ) from exc

        return {
            "model_version": request.app.state.model_version,
            "detections": detections,
        }

    