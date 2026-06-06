package com.healyn.common.error;

import org.springframework.http.HttpStatus;

public class NotFoundException extends DomainException {

    public NotFoundException(String code, String message) {
        super(code, HttpStatus.NOT_FOUND, message);
    }
}
