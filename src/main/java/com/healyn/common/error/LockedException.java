package com.healyn.common.error;

import org.springframework.http.HttpStatus;

public class LockedException extends DomainException {

    public LockedException(String code, String message) {
        super(code, HttpStatus.LOCKED, message);
    }
}
