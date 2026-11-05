package com.zimpic.backend.dto.response.vision;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class VisionRoomResponse {

  @JsonProperty("vision_image_id")
  private final Long visionImageId;

  @JsonProperty("room_type")
  private final String roomType;

  @JsonProperty("image_url")
  private final String imageUrl;

  private final List<VisionDetectionResponse> detections;
}
