package com.zimpic.backend.dto.response;

import com.zimpic.backend.entity.Furniture;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@Getter
@AllArgsConstructor
@ToString
public class FurnitureResponse {
  private final Long id;
  private final String category;
  private final String nameEn;
  private final String nameKr;
  private final String description;
  private final String referenceModel;
  private final String referenceUrl;
  private final Integer widthCm;
  private final Integer depthCm;
  private final Integer heightCm;
  private final boolean needsDisassembly;

  public static FurnitureResponse entityToDto(Furniture furniture){
    return new FurnitureResponse(
        furniture.getId(),
        furniture.getCategory().name(),
        furniture.getNameEn(),
        furniture.getNameKr(),
        furniture.getDescription(),
        furniture.getReferenceModel(),
        furniture.getReferenceUrl(),
        furniture.getWidthCm(),
        furniture.getDepthCm(),
        furniture.getHeightCm(),
        furniture.isNeedsDisassembly()
    );
  }
}
