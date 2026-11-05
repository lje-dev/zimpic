package com.zimpic.backend.service;

import com.zimpic.backend.client.yolo.YoloClient;
import com.zimpic.backend.client.yolo.dto.InferenceResponse;
import com.zimpic.backend.dto.request.RoomUploadRequest;
import com.zimpic.backend.dto.response.vision.VisionUploadResponse;
import com.zimpic.backend.exception.BusinessException;
import com.zimpic.backend.exception.ErrorCode;
import jakarta.servlet.Servlet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tools.jackson.core.type.TypeReference;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.zimpic.backend.service.ImageStorageService.StoredImage;

import java.time.Instant;
import java.util.*;

/**
 * 추론요청 검증, 이미지 파일 저장, FastAPI 추론,
 * DB 저장, 실패 시 파일 보관과 로그 기록을 순서대로 진행하는 서비스

 * 참고) VisionPersistenceService와의 차이점! :
 * persistence는 DB에 저장하기 위해 짧은 트랜잭션을 수행.
 * 검증 및 다른 작업들을 여기에서 한번만 확인하는 용도
 */

@Slf4j
@Service
public class VisionService {

  private final ObjectMapper objectMapper;   // JSON 문자열을 Java 객체로 바꿔 주는 도구
  private final ImageStorageService imageStorageService;
  private final YoloClient yoloClient;
  private final VisionPersistenceService visionPersistenceService;
  private final VisionLogService visionLogService;
  private final ImageFileValidator imageFileValidator;

  public VisionService(
      ObjectMapper objectMapper,
      ImageStorageService imageStorageService,
      YoloClient yoloClient,
      VisionPersistenceService visionPersistenceService,
      VisionLogService visionLogService,
      @Value("${vision.max-file-size}") long maxFileSize, ImageFileValidator imageFileValidator) {
    this.objectMapper = objectMapper;
    this.imageStorageService = imageStorageService;
    this.yoloClient = yoloClient;
    this.visionPersistenceService = visionPersistenceService;
    this.visionLogService = visionLogService;
    this.imageFileValidator = imageFileValidator;
  }

  // 요청 1회에 들어있는 모든 방을 처리하는 메서드
  // 순서) 1. 요청 검증 -> 2. 방별 파일 저장과 YOLO추론 -> 3. 전체 결과를 DB에 저장
  // 참고) 중간에 실패 시 VisionLog에 기록
  public VisionUploadResponse analyze(String roomsJson, List<MultipartFile> files) {
    UUID requestId = UUID.randomUUID();

    // 이미지 파일 저장에 성공한 이미지만 이미지-방을 짝지어서 담아두기 (실패 시 순회하며 확인하기 위함)
    // LinkedHashMap은 파일을 저장한 순서를 유지함.
    Map<StoredImage, RoomUploadRequest> storedRooms = new LinkedHashMap<>();

    // 파일 저장과 추론을 모두 마친 방을 임시 객체에 저장.
    // 한 요청에 여러 방이 있는 경우 모아서 한번에 DB에 전달
    List<VisionPreparedRoom> preparedRooms = new ArrayList<>();

    // 처리 중인 방과 단계를 담아두고 실패 시 오류 로그에 사용하여 원인을 찾는 용도
    RoomUploadRequest currentRoom = null;
    String stage = "REQUEST_VALIDATION";

    try {
      // 요청 Json을 객체 배열로 전환해서 담기
      List<RoomUploadRequest> rooms = parseRooms(roomsJson);
      // 1. 요청 검증하기
      // 1-1. 방 하나에 이미지 파일 하나가 잘 짝지어 졌는지 검증
      validateRequest(rooms, files);

      for (RoomUploadRequest room : rooms) {
        currentRoom = room;
        MultipartFile file = files.get(room.getFileIndex());

        // 1-1. 이미지 확장자, 크기, 사이즈, 손상 여부 확인 후 확장자 가져오기
        stage = "IMAGE_VALIDATION";
        String extension = imageFileValidator.validate(file);

        // 1-2. 이미지를 실제 물리적으로 저장하고, 요청 방정보와 함께 객체에 담아두기
        stage = "FILE_STORAGE";
        StoredImage stored = imageStorageService.store(file, extension);
        Instant uploadAt = Instant.now();
        storedRooms.put(stored, room);

        // 2. 이미지 추론
        stage = "INFERENCE";
        InferenceResponse inference = yoloClient.inference(file);

        // /media/vision/xxx.jpg같은 url 앞에 https://도메인 주소를 붙여 전체 주소를 생성
        String absoluteUrl = ServletUriComponentsBuilder
            .fromCurrentContextPath()
            .path(stored.getPublicPath())
            .toUriString();

        // 아직 DB를 저장하기 전, 임시 객체 목록에 현재 방의 정보를 담아두기
        preparedRooms.add(new VisionPreparedRoom(room, stored, inference, absoluteUrl, uploadAt));
      }

      // 3. 방마다 이미지 저장 및 추론이 끝나면 전체 요청 결과를 DB에 저장하고
      // 요청의 응답 값으로 돌려줌
      stage = "DB_SAVE";
      return visionPersistenceService.persist(preparedRooms);

    } catch (RuntimeException exception) {

      // 이미지-방 짝지어서 담아둔 Map을 하나씩 꺼내서 실패 로그에 기록하기
      for (Map.Entry<StoredImage, RoomUploadRequest> entry : storedRooms.entrySet()) {
        RoomUploadRequest room = entry.getValue();
        String retainedPath = entry.getKey().getPath().getFileName().toString();

        recordFailureQuietly(
            requestId,
            room.getRoomType(),
            room.getFileIndex(),
            stage,
            exception,
            retainedPath);
      }

      // 파일 저장 전 or 파일 저장 시 실패했다면 경로가 없는데, 그 경우도 로그로 남기기
      if (storedRooms.isEmpty() || (currentRoom != null && !storedRooms.containsValue(currentRoom))) {
        recordFailureQuietly(
            requestId,
            currentRoom == null ? null : currentRoom.getRoomType(),
            currentRoom == null ? null : currentRoom.getFileIndex(),
            stage,
            exception,
            null);
      }

      throw exception;
    }
  }


  // 클라이언트에서 요청한 Json을 Java가 사용할 객체(RoomUploadRequest 타입의 리스트)로 변환하는 보조메서드
  private List<RoomUploadRequest> parseRooms(String roomsJson) {
    if (roomsJson == null || roomsJson.isBlank()) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST, "rooms는 필수입니다.");
    }

    try {
      // ObjectMapper가 Json->List<RoomUploadRequest>로 변환
      List<RoomUploadRequest> rooms = objectMapper.readValue(
          roomsJson,
          new TypeReference<List<RoomUploadRequest>>() {}
      );

      if (rooms == null || rooms.isEmpty()) {
        throw new BusinessException(ErrorCode.INVALID_REQUEST, "rooms는 한 개 이상이어야 합니다.");
      }
      return rooms;
    } catch (JacksonException exception) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST, "rooms는 Json배열 형식이어야 합니다.");
    }
  }


  // 각 방의 file_index가 files 목록에서 실제 파일 하나를 올바르게 가리키는지 확인하는 메서드
  private void validateRequest(List<RoomUploadRequest> rooms, List<MultipartFile> files) {
    if (files == null || files.isEmpty()) {
      throw new BusinessException(ErrorCode.IMAGE_FILE_REQUIRED);
    }
    if (rooms.size() != files.size()) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST, "rooms와 files의 개수가 일치해야 합니다.");
    }

    // 같은 파일을 다른 룸이 중복으로 가르키지 검증
    Set<Integer> usedIndexes = new HashSet<>();
    for (RoomUploadRequest room : rooms) {
      if (room == null || room.getRoomType() == null || room.getRoomType().isBlank()) {
        throw new BusinessException(ErrorCode.INVALID_REQUEST, "각 room에는 room_type이 필요합니다.");
      }
      Integer index = room.getFileIndex();
      if (index == null || index < 0 || index >= files.size()) {
        throw new BusinessException(ErrorCode.INVALID_REQUEST, "file_index가 files 범위를 벗어났습니다.");
      }

      // set에 이미 있는 인덱스를 추가할 경우 false를 반환함
      if (!usedIndexes.add(index)) {
        throw new BusinessException(ErrorCode.INVALID_REQUEST, "file_index는 중복될 수 없습니다.");
      }
    }
  }


  // 서비스 실패 시 조용히 VisionLogService로 로그를 기록하는 보조 메서드
  private void recordFailureQuietly(UUID requestId, String roomType, Integer fileIndex, String stage, RuntimeException exception, String retainedPath) {
    try {
      visionLogService.recordFailure(requestId, roomType, fileIndex, stage, exception, retainedPath);
    } catch (RuntimeException logFailure) {
      log.error("Vision 오류 로그 저장 실패 : requestId={}, stage={}", requestId, stage, logFailure);
      log.error("Vision 처리 실패 원문 : requestId={}", requestId, exception);
    }
  }

}
