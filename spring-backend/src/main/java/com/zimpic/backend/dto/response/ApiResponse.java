package com.zimpic.backend.dto.response;

public record ApiResponse<T>(boolean success, T data) {
	public static <T> ApiResponse<T> success(T data) {
		return new ApiResponse<T>(true, data);
	}
}
