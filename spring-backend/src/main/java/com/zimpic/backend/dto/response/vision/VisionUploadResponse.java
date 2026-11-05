package com.zimpic.backend.dto.response.vision;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class VisionUploadResponse {
  private final List<VisionRoomResponse> results;
}
