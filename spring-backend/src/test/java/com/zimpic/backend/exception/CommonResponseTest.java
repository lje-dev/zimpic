package com.zimpic.backend.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.zimpic.backend.dto.response.ApiResponse;

public class CommonResponseTest {

    // 정상 응답 확인
    @Test 
    void successResponseContainsData(){
        ApiResponse<String> response = ApiResponse.success("data");
        
        assertTrue(response.success());
        assertEquals("data", response.data());        
    }
}
