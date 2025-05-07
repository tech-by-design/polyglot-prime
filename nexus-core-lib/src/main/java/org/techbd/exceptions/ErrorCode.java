package org.techbd.exceptions;

public enum ErrorCode {
    INVALID_JSON("TECHBD-1000", "Invalid or Partial JSON"),
    INVALID_BUNDLE_PROFILE("TECHBD-1001",
            "The provided bundle profile URL is invalid. Please check and enter the correct bundle profile url"),
    BUNDLE_PROFILE_URL_IS_NOT_PROVIDED("TECHBD-1002", "Bundle profile url must be provided");

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
