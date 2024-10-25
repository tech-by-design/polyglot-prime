package org.techbd.service.constants;

public enum ErrorCode {
    INVALID_JSON("TECHBD-1000", "Invalid or Partial JSON");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}

