package com.databuff.digitalexpert.common;

import com.databuff.digitalexpert.dao.enums.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final HttpStatus httpStatus;

    public BusinessException(ErrorCode errorCode, String detail, HttpStatus httpStatus) {
        super(detail);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public static BusinessException badRequest(ErrorCode errorCode, String detail) {
        return new BusinessException(errorCode, detail, HttpStatus.BAD_REQUEST);
    }

    public static BusinessException notFound(ErrorCode errorCode, String detail) {
        return new BusinessException(errorCode, detail, HttpStatus.NOT_FOUND);
    }

    public static BusinessException conflict(ErrorCode errorCode, String detail) {
        return new BusinessException(errorCode, detail, HttpStatus.CONFLICT);
    }

    public static BusinessException internal(ErrorCode errorCode, String detail) {
        return new BusinessException(errorCode, detail, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
