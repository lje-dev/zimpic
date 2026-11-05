package com.zimpic.backend.controller;

import com.zimpic.backend.dto.response.ApiResponse;
import com.zimpic.backend.dto.response.vision.VisionRoomResponse;
import com.zimpic.backend.dto.response.vision.VisionUploadResponse;
import com.zimpic.backend.service.VisionService;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/vision")
@RequiredArgsConstructor
public class VisionController {

  private final VisionService visionService;
  private final ObjectMapper objectMapper;

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<VisionUploadResponse> upload(
      @Parameter(
          description = "방 목록을 JSON 배열 문자열로 입력",
          example = "[{\"room_type\":\"LIVING\",\"file_index\":0,\"sort_order\":1}]"
      )
      @RequestPart("rooms") String rooms,
      @RequestPart("files") List<MultipartFile> files,
      HttpServletRequest request
      ) {
    log.info("[VisionUpload] Request: method={}, uri={}, rooms={}, files={}",
        request.getMethod(), request.getRequestURI(), rooms,
        files.stream().map(MultipartFile::getOriginalFilename).toList());
    ApiResponse<VisionUploadResponse> response = ApiResponse.success(visionService.analyze(rooms, files));
    log.info("[VisionUpload] Response={}", objectMapper.writeValueAsString(response));
    return response;
  }

}
