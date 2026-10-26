package com.zimpic.backend.repository;

import java.util.List;
import java.util.Optional;

import com.zimpic.backend.entity.type.FurnitureCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import com.zimpic.backend.entity.Furniture;

public interface FurnitureRepository extends JpaRepository<Furniture, Long>{

    Optional<Furniture> findByNameEn(String nameEn);
    
    Optional<Furniture> findByYoloId(Integer yoloId);

    // 전달받은 카테고리 인자를 제외한(Not) 모든 가구 조회하기
    List<Furniture> findAllByCategoryNotOrderByIdAsc(FurnitureCategory category);
}
