package com.healyn.common.error;

import org.springframework.http.HttpStatus;

public abstract class DomainException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    protected DomainException(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    protected DomainException(String code, HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }
}
