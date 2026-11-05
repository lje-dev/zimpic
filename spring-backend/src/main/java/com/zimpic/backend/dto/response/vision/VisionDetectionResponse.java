package com.zimpic.backend.dto.response.vision;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.zimpic.backend.entity.Furniture;
import com.zimpic.backend.entity.VisionDetection;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class VisionDetectionResponse {

  @JsonProperty("detection_id")
  private final Long detectionId;

  @JsonProperty("yolo_id")
  private final Integer yoloId;

  @JsonProperty("yolo_class")
  private final String yoloClass;

  @JsonProperty("furniture_id")
  private final Long furnitureId;

  @JsonProperty("name_kr")
  private final String nameKr;

  @JsonProperty("name_en")
  private final String nameEn;

  private final String category;

  private final BigDecimal confidence;

  private final VisionBoundingBoxResponse bbox;

  private final String description;

  @JsonProperty("reference_model")
  private final String referenceModel;

  @JsonProperty("reference_url")
  private final String referenceUrl;

  @JsonProperty("guide_size_cm")
  private final VisionGuideSizeResponse guideSizeCm;

  @JsonProperty("needs_disassembly")
  private final boolean needsDisassembly;


  public static VisionDetectionResponse entityToDto(VisionDetection detection) {
    // 바운딩박스가 비어있는 경우 비운채로 응답
    VisionBoundingBoxResponse bbox = detection.getBboxX() == null ? null
        : new VisionBoundingBoxResponse(
            detection.getBboxX().doubleValue(),
            detection.getBboxY().doubleValue(),
            detection.getBboxW().doubleValue(),
            detection.getBboxH().doubleValue()
        );

    // yoloId로 가구를 찾을 수 없는 경우 furniture관련 값을 null로 응답
    Furniture furniture = detection.getFurniture();
    if (furniture == null) {
      return new VisionDetectionResponse(
          detection.getId(),
          detection.getYoloId(),
          detection.getYoloClass(),
          null,
          null,
          null,
          null,
          detection.getConfidence(),
          bbox,
          null,
          null,
          null,
          null,
          false
      );
    }

    VisionGuideSizeResponse guideSize = new VisionGuideSizeResponse(
        furniture.getWidthCm(),
        furniture.getDepthCm(),
        furniture.getHeightCm()
    );

    return new VisionDetectionResponse(
        detection.getId(),
        detection.getYoloId(),
        detection.getYoloClass(),
        furniture.getId(),
        furniture.getNameKr(),
        furniture.getNameEn(),
        furniture.getCategory().name(),
        detection.getConfidence(),
        bbox,
        furniture.getDescription(),
        furniture.getReferenceModel(),
        furniture.getReferenceUrl(),
        guideSize,
        furniture.isNeedsDisassembly()
    );
  }

}
