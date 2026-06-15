package com.healyn.auth.service;

import com.healyn.common.error.ErrorCode;
import com.healyn.common.error.UnprocessableException;
import org.springframework.stereotype.Component;

import java.util.Set;

/// Single source of truth for password acceptance at registration and reset (audit L2). Length
/// 10–128 keeps the floor reasonable while bounding Argon2 work (a 128-char cap is real DoS
/// protection — see PasswordHasher). The null-byte guard rejects a classic truncation trick.
/// Beyond length we reject a small denylist of the most-guessed passwords; NIST 800-63B favours
/// a breached-password check over composition rules, and this is the dependency-free floor of
/// that — swap in a full Have-I-Been-Pwned range check when an adapter is added.
@Component
public class PasswordPolicy {

    private static final int MIN_LENGTH = 10;
    private static final int MAX_LENGTH = 128;

    // Most-common passwords that still satisfy a 10-char minimum. Compared case-insensitively.
    private static final Set<String> DENYLIST = Set.of(
            "password",
            "password1",
            "password123",
            "passw0rd123",
            "1234567890",
            "12345678901",
            "123456789012",
            "qwertyuiop",
            "qwerty12345",
            "iloveyou123",
            "letmein1234",
            "adminadmin",
            "welcome1234",
            "healyn12345");

    public void validate(String pw) {
        if (pw == null || pw.length() < MIN_LENGTH || pw.length() > MAX_LENGTH) {
            throw new UnprocessableException(ErrorCode.UNPROCESSABLE,
                    "Password must be " + MIN_LENGTH + "-" + MAX_LENGTH + " characters");
        }
        if (pw.indexOf('\0') >= 0) {
            throw new UnprocessableException(ErrorCode.UNPROCESSABLE, "Password contains forbidden character");
        }
        if (DENYLIST.contains(pw.toLowerCase())) {
            throw new UnprocessableException(ErrorCode.UNPROCESSABLE, "Password is too common");
        }
    }
}
