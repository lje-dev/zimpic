from fastapi import APIRouter

# tags는 Swagger문서에서 API를 묶어 보여줄 이름을 설정
router = APIRouter(tags=["health"])


# FastAPI 서버의 응답 가능 상태 확인
@router.get("/health")
def health():
    return {"status": "ok"}