package com.healyn.auth.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokensTest {

    @Test
    void generated_tokens_are_unique_and_long() {
        String a = RefreshTokens.generate();
        String b = RefreshTokens.generate();
        assertThat(a).isNotEqualTo(b);
        assertThat(a.length()).isGreaterThanOrEqualTo(42);
    }

    @Test
    void hash_is_deterministic_and_32_bytes() {
        byte[] h1 = RefreshTokens.hash("token-value");
        byte[] h2 = RefreshTokens.hash("token-value");
        assertThat(h1).isEqualTo(h2).hasSize(32);
    }
}
