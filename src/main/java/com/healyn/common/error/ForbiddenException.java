package com.healyn.common.error;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends DomainException {

    public ForbiddenException(String code, String message) {
        super(code, HttpStatus.FORBIDDEN, message);
    }
}
