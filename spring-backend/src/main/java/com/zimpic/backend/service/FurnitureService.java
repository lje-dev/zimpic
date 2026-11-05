package com.zimpic.backend.service;

import com.zimpic.backend.dto.response.FurnitureResponse;
import com.zimpic.backend.entity.type.FurnitureCategory;
import com.zimpic.backend.repository.FurnitureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FurnitureService {

  private final FurnitureRepository furnitureRepository;

  @Transactional(readOnly = true)
  public List<FurnitureResponse> findAll() {
    return furnitureRepository.findAllByCategoryNotOrderByIdAsc(FurnitureCategory.ETC)
        .stream().map(FurnitureResponse::entityToDto).toList();
  }

}
