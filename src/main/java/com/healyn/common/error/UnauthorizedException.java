package com.healyn.common.error;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends DomainException {

    public UnauthorizedException(String code, String message) {
        super(code, HttpStatus.UNAUTHORIZED, message);
    }
}
