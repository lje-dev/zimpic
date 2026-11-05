package com.zimpic.backend.controller;

import com.zimpic.backend.dto.response.ApiResponse;
import com.zimpic.backend.dto.response.FurnitureResponse;
import com.zimpic.backend.service.FurnitureService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/policy/furniture")
@RequiredArgsConstructor
public class FurnitureController {

  private final FurnitureService furnitureService;

  @GetMapping
  public ApiResponse<List<FurnitureResponse>> findAll(HttpServletRequest request) {
    log.info("[Furniture List] Request: method={}, uri={}",request.getMethod(), request.getRequestURI());

    List<FurnitureResponse> response = furnitureService.findAll();

    log.info("[Furniture List] Response: size={} furniture={}", response.size(), response);

    return ApiResponse.success(response);
  }

}
