package com.zimpic.backend.dto.response.vision;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.JoinColumn;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class VisionBoundingBoxResponse {

  private final double x;
  private final double y;

  @JsonProperty("w")
  private final double width;

  @JsonProperty("h")
  private final double height;

}
