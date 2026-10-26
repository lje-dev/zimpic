package com.zimpic.backend.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.zimpic.backend.entity.Furniture;
import com.zimpic.backend.entity.type.FurnitureCategory;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringBootTest
@Transactional
public class FurnitureRepositoryTest {

    @Autowired
    private FurnitureRepository furnitureRepository;

    @Test
    void saveAndFindsFurniture() {
        Furniture furniture = new Furniture(
            FurnitureCategory.GENERAL_FURNITURE,
            9999,
            "jpa_test furniture",
            "Jpa 가구 테스트",
            "Repository 연결 확인용 데이터",
            null,
            null,
            100,
            50,
            80,
            new BigDecimal("1.0"),
            false,
            true,
            true
        );

        // sql실행 + 생성된 아이디 저장
        Furniture saved = furnitureRepository.saveAndFlush(furniture);
        log.info("saved={}", saved);

        assertNotNull(saved.getId());

        Furniture found = furnitureRepository.findByNameEn("jpa_test furniture").orElseThrow();

        assertEquals(saved.getId(), found.getId());
        assertEquals("Jpa 가구 테스트", found.getNameKr());
        assertTrue(found.isStackable());
    }
}
