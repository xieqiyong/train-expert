package com.databuff.digitalexpert.dao.response;

import com.databuff.digitalexpert.dao.enums.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ApiResponse<T> {

    private final boolean success;
    private final int code;
    private final String message;
    private final T data;

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, 0, "success", data);
    }

    public static ApiResponse<Void> success() {
        return new ApiResponse<>(true, 0, "success", null);
    }

    public static ApiResponse<Void> failure(ErrorCode errorCode) {
        return failure(errorCode, errorCode.getMessage());
    }

    public static ApiResponse<Void> failure(ErrorCode errorCode, String message) {
        return new ApiResponse<>(false, errorCode.getCode(), message, null);
    }

    public static ApiResponse<Void> error(int errorCode, String message) {
        return new ApiResponse<>(false, errorCode, message, null);
    }
}
