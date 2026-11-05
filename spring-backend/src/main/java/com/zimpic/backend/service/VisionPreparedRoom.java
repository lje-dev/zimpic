package com.zimpic.backend.service;

import com.zimpic.backend.client.yolo.dto.InferenceResponse;
import com.zimpic.backend.dto.request.RoomUploadRequest;
import com.zimpic.backend.service.ImageStorageService.StoredImage;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

/**
 * 이미지 파일 저장과 이미지 추론을 마친 방 정보를 객체로 생성해 메모리에 임시보관하는 용도.
 * 방이 여러개일 경우 여러 방의 정보를 DB에 한꺼번에 전달해,
 * DB 저장 시 한 트랜잭션에 짧게 열어두고 저장할 수 있음.
**/

@Getter
@AllArgsConstructor
class VisionPreparedRoom {

  private final RoomUploadRequest room;
  private final StoredImage stored;
  private final InferenceResponse inference;
  private final String absoluteImageUrl;
  private final Instant uploadedAt;

}
