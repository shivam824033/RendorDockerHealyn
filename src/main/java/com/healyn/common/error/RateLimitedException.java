package com.healyn.common.error;

import org.springframework.http.HttpStatus;

public class RateLimitedException extends DomainException {

    public RateLimitedException(String code, String message) {
        super(code, HttpStatus.TOO_MANY_REQUESTS, message);
    }
}
