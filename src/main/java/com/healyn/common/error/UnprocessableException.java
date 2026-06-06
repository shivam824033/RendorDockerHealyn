package com.healyn.common.error;

import org.springframework.http.HttpStatus;

public class UnprocessableException extends DomainException {

    public UnprocessableException(String code, String message) {
        super(code, HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
