package com.zimpic.backend.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class RoomUploadRequest {

  @JsonProperty("room_type")
  private String roomType;

  @JsonProperty("file_index")
  private Integer fileIndex;

  @JsonProperty("sort_order")
  private Integer sortOrder;
}
