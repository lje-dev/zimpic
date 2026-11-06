package com.zimpic.backend.service;

import com.zimpic.backend.client.yolo.dto.BoundingBoxResponse;
import com.zimpic.backend.client.yolo.dto.DetectionResponse;
import com.zimpic.backend.dto.response.vision.VisionDetectionResponse;
import com.zimpic.backend.dto.response.vision.VisionRoomResponse;
import com.zimpic.backend.dto.response.vision.VisionUploadResponse;
import com.zimpic.backend.entity.Furniture;
import com.zimpic.backend.entity.VisionDetection;
import com.zimpic.backend.entity.VisionImage;
import com.zimpic.backend.repository.FurnitureRepository;
import com.zimpic.backend.repository.VisionDetectionRepository;
import com.zimpic.backend.repository.VisionImageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * VisionImage, VisionDetection을 함께 짧은 트랜잭션에서 DB에 저장하는 서비스.
 
 * 참고) VisionService와의 차이점! :
 * 요청(방,파일형식) 검증, 이미지 파일 저장, 이미지 추론, 방 결과 임시 모음(VisionPreparedRoom)등 
 * 시간이 오래걸리는 작업을 진행하는 용도로 분리해둠.
**/


@Slf4j
@Service
public class VisionPersistenceService {

  private final VisionImageRepository imageRepository;
  private final VisionDetectionRepository detectionRepository;
  private final FurnitureRepository furnitureRepository;
  private final Duration successRetention;

  public VisionPersistenceService(
      VisionImageRepository imageRepository,
      VisionDetectionRepository detectionRepository,
      FurnitureRepository furnitureRepository,
      @Value("${vision.success-retention}") Duration successRetention) {
    this.imageRepository = imageRepository;
    this.detectionRepository = detectionRepository;
    this.furnitureRepository = furnitureRepository;
    this.successRetention = successRetention;
  }


  // 한 요청에 있는 모든 방과 탐지 정보를 같은 DB트랜잭션에 저장
  @Transactional
  public VisionUploadResponse persist(List<VisionPreparedRoom> preparedRooms) {

    List<VisionRoomResponse> roomResponses = new ArrayList<>();

    // 1. 방 정보 순회
    for (VisionPreparedRoom prepared : preparedRooms) {

      // 1-1. 이미지 정보 저장 (물리 저장은 업로드 시 VisionService 단계에서 완료. 여기서 저장하는 건 DB row)
      //      이미지 정보를 먼저 저장해야 탐지 결과가 이 이미지를 참조할 수 있으므로 선행함
      VisionImage image = imageRepository.save(new VisionImage(
        prepared.getRoom().getRoomType(),
        prepared.getStored().getPublicPath(),
        prepared.getStored().getOriginalFilename(),
        prepared.getUploadedAt().plus(successRetention)
      ));


      // 2. 가구 추론 결과 순회하며 방 하나의 추론 리스트를 저장
      List<VisionDetectionResponse> detections = new ArrayList<>();

      for (DetectionResponse detection : prepared.getInference().getDetections()) {
        // 2-1. YoloId로 가구정보 찾기
        Furniture furniture = furnitureRepository.findByYoloId(detection.getClassId()).orElse(null);

        // 2-2. 바운딩박스가 null인 경우 null로 대입
        BoundingBoxResponse bbox = detection.getBbox();
        VisionDetection visionDetection = new VisionDetection(
            image,
            detection.getClassId(),
            detection.getClassName(),
            furniture,
            BigDecimal.valueOf(detection.getConfidence()),
            bbox == null ? null : BigDecimal.valueOf(bbox.getX()),
            bbox == null ? null : BigDecimal.valueOf(bbox.getY()),
            bbox == null ? null : BigDecimal.valueOf(bbox.getWidth()),
            bbox == null ? null : BigDecimal.valueOf(bbox.getHeight())
        );

        // 2-3. visionDetection을 DB에 저장
        detectionRepository.save(visionDetection);

        // 2-4. 저장된 VisionDetection을 응답 DTO로 변환해 응답 목록에 추가
        VisionDetectionResponse visionDetectionResponse = VisionDetectionResponse.entityToDto(visionDetection);
        detections.add(visionDetectionResponse);


        // 2-5. 에어컨의 경우 해당 가구 목록 뒤에 실외기 추가하기
        String outdoorName = null;
        if (furniture != null) {
          switch (furniture.getNameEn()) {
            case "air_conditioner_wall" :
              outdoorName = "ac_outdoor_wall";
              break;
            case "air_conditioner_stand" :
              outdoorName = "ac_outdoor_stand";
              break;
            default :
              break;
          }
        }
        if (outdoorName != null) {
          Furniture outdoorFurniture = furnitureRepository.findByNameEn(outdoorName).orElse(null);
          if (outdoorFurniture != null) {
            VisionDetection outdoorDetection = new VisionDetection(
              image,
              null,
              outdoorName,
              outdoorFurniture,
              null,
              null,
              null,
              null,
              null
            );
            detectionRepository.save(outdoorDetection);
            VisionDetectionResponse visionDetectionOutdoorResponse = VisionDetectionResponse.entityToDto(outdoorDetection);
            detections.add(visionDetectionOutdoorResponse);
          }
        }
      }

      // 3. 저장한 방 이미지 정보와 탐지 결과를 방 하나의 응답으로 묶기
      VisionRoomResponse visionRoomResponse = new VisionRoomResponse(
        image.getId(),
        image.getRoomType(),
        prepared.getAbsoluteImageUrl(),
        detections
      );

      // 4. 방 응답을 전체 방 목록에 추가
      roomResponses.add(visionRoomResponse);
    }

    return new VisionUploadResponse(roomResponses);
  }
}
