package com.signal.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ApiResponse<T>(T data, ApiError error) {
    
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(data, null);
    }
    
    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(null, new ApiError(code, message));
    }
    
    public record ApiError(String code, String message) {}
}
