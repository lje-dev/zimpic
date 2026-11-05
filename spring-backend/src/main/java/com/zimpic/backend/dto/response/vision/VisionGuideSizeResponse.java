package com.zimpic.backend.dto.response.vision;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class VisionGuideSizeResponse {

  @JsonProperty("width_cm")
  private final Integer widthCm;

  @JsonProperty("depth_cm")
  private final Integer depthCm;

  @JsonProperty("height_cm")
  private final Integer heightCm;

}
