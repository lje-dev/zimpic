from pathlib import Path
from ultralytics import YOLO
from ultralytics.utils import ThreadingLocked


# YOLO 모델 불러오기
def load_model(model_path: Path, device: str):
    model = YOLO(str(model_path))
    model.to(device)
    return model


# 이미지에서 객체 추론 
@ThreadingLocked() # 동시성 제어
def run_inference(model, image_path: Path, image_size: int, confidencd: float):
    
    ########## 이미지에서 객체 탐지 ########## 
    results = model.predict(
        source=str(image_path),
        imgsz=image_size,
        conf=confidencd,
        save=False,     # 탐지 결과 이미지 -> 서버 저장 설정
        verbose=False,  # Ultralytics의 상세 실행로그를 출력 설정
    )

    # 추론 후 탐지된 객체가 없으면 빈 배열 반환
    if not results or results[0].boxes is None:
        return []

    # api는 이미지 1장만 처리하므로 첫번째 결과만 사용
    result = results[0]

    # YOLO 결과 구조
    # result                  이미지 한 장의 YOLO 추론 결과
    # ├── orig_shape          원본 이미지 크기 (높이, 너비)
    # ├── names               클래스 번호와 이름
    # └── boxes               탐지된 바운딩박스 정보
    #     ├── xyxy            좌측 상단과 우측 하단 좌표
    #     ├── cls             클래스 번호
    #     └── conf            신뢰도

    

    ##########  YOLO 바운딩박스 좌표 -> api응답용 좌표로 변환하기 ########## 
    image_height, image_width = result.orig_shape
    detections = []

    # PyTorch텐서를 Python리스트로 변환하기
    # 각 객체의 박스 좌표, 클래스 번호, 신뢰도를 같은 순서로 묶어 순회
    for xyxy, class_value, score in zip(
        result.boxes.xyxy.cpu().tolist(), # GPU로 추론했다면 GPU메모리에 있을 수 있기때문에 CPU메모리로 가져옴
        result.boxes.cls.cpu().tolist(),
        result.boxes.conf.cpu().tolist()
    ):
        x1, y1, x2, y2 = xyxy
        width = max(0.0, x2 - x1)
        height = max(0.0, y2 - y1)
        class_id = int(class_value)

        # 응답 객체를 딕셔너리로 생성
        detections.append(
            {
                "class_id": class_id,
                "class_name": result.names.get(class_id, str(class_id)),
                "confidence": float(score),
                "bbox": {
                    "x": float((x1 + width / 2) / image_width),
                    "y": float((y1 + height / 2) / image_height),
                    "width": float(width / image_width),
                    "height": float(height / image_height)
                },
            }
        )

    # 전체 탐지 결과 반환
    return detections

