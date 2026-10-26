package com.zimpic.backend.controller;

import com.zimpic.backend.dto.response.FurnitureResponse;
import com.zimpic.backend.service.FurnitureService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/policy/furniture")
@RequiredArgsConstructor
public class FurnitureController {

  private final FurnitureService furnitureService;

  @GetMapping
  public List<FurnitureResponse> findAll() {
    return furnitureService.findAll();
  }

}
