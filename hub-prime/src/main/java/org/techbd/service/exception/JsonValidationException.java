package org.techbd.service.exception;

import org.techbd.service.constants.ErrorCode;

public class JsonValidationException extends RuntimeException {
    private final String errorCode;

    public JsonValidationException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode.getCode();
    }

    public String getErrorCode() {
        return errorCode;
    }
}
