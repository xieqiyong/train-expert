package com.databuff.digitalexpert.facade.common;

import com.databuff.digitalexpert.dao.enums.ErrorCode;
import lombok.Getter;

@Getter
public class FacadeBusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public FacadeBusinessException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }

    public static FacadeBusinessException badRequest(ErrorCode errorCode, String detail) {
        return new FacadeBusinessException(errorCode, detail);
    }

    public static FacadeBusinessException notFound(ErrorCode errorCode, String detail) {
        return new FacadeBusinessException(errorCode, detail);
    }

    public static FacadeBusinessException conflict(ErrorCode errorCode, String detail) {
        return new FacadeBusinessException(errorCode, detail);
    }

    public static FacadeBusinessException internal(ErrorCode errorCode, String detail) {
        return new FacadeBusinessException(errorCode, detail);
    }
}
