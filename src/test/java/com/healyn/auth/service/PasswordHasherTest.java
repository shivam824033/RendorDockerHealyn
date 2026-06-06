package com.healyn.auth.service;

import com.healyn.auth.config.AuthProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher(new AuthProperties.Password("test-pepper-value"));

    @Test
    void hash_then_matches_returns_true() {
        var h = hasher.hash("correct horse battery staple");
        assertThat(hasher.matches("correct horse battery staple", h.hash(), h.salt())).isTrue();
    }

    @Test
    void matches_returns_false_for_wrong_password() {
        var h = hasher.hash("right-password-1");
        assertThat(hasher.matches("wrong-password-1", h.hash(), h.salt())).isFalse();
    }

    @Test
    void salts_differ_per_call_for_same_password() {
        var a = hasher.hash("same-password");
        var b = hasher.hash("same-password");
        assertThat(a.salt()).isNotEqualTo(b.salt());
        assertThat(a.hash()).isNotEqualTo(b.hash());
    }

    @Test
    void blank_pepper_is_rejected() {
        assertThatThrownBy(() -> new PasswordHasher(new AuthProperties.Password("")))
                .isInstanceOf(IllegalStateException.class);
    }
}
