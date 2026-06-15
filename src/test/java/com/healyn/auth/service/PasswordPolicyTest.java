package com.healyn.auth.service;

import com.healyn.common.error.UnprocessableException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void accepts_a_reasonable_password() {
        assertThatCode(() -> policy.validate("correct-horse-battery")).doesNotThrowAnyException();
    }

    @Test
    void rejects_too_short() {
        assertThatThrownBy(() -> policy.validate("short"))
                .isInstanceOf(UnprocessableException.class)
                .hasMessageContaining("10-128");
    }

    @Test
    void rejects_too_long() {
        assertThatThrownBy(() -> policy.validate("a".repeat(129)))
                .isInstanceOf(UnprocessableException.class)
                .hasMessageContaining("10-128");
    }

    @Test
    void rejects_null() {
        assertThatThrownBy(() -> policy.validate(null)).isInstanceOf(UnprocessableException.class);
    }

    @Test
    void rejects_null_byte() {
        assertThatThrownBy(() -> policy.validate("abcdef\0ghij"))
                .isInstanceOf(UnprocessableException.class)
                .hasMessageContaining("forbidden");
    }

    @Test
    void rejects_common_password_case_insensitively() {
        assertThatThrownBy(() -> policy.validate("Password123"))
                .isInstanceOf(UnprocessableException.class)
                .hasMessageContaining("too common");
    }
}
