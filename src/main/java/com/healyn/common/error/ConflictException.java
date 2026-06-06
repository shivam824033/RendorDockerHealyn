package com.healyn.common.error;

import org.springframework.http.HttpStatus;

public class ConflictException extends DomainException {

    public ConflictException(String code, String message) {
        super(code, HttpStatus.CONFLICT, message);
    }
}
