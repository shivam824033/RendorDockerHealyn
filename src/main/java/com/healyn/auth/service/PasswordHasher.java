package com.healyn.auth.service;

import com.healyn.auth.config.AuthProperties;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class PasswordHasher {

    private static final int SALT_BYTES = 16;
    private static final int HASH_LENGTH = 32;
    private static final int PARALLELISM = 1;
    private static final int MEMORY_KB = 64 * 1024;
    private static final int ITERATIONS = 3;

    private final SecureRandom random = new SecureRandom();
    private final Argon2PasswordEncoder encoder =
            new Argon2PasswordEncoder(SALT_BYTES, HASH_LENGTH, PARALLELISM, MEMORY_KB, ITERATIONS);
    private final byte[] pepper;

    public PasswordHasher(AuthProperties.Password props) {
        String configured = props.pepper();
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("healyn.password.pepper is not configured");
        }
        this.pepper = configured.getBytes(StandardCharsets.UTF_8);
    }

    public Hashed hash(String rawPassword) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        String encoded = encoder.encode(mix(rawPassword, salt));
        return new Hashed(encoded, salt);
    }

    public boolean matches(String rawPassword, String passwordHash, byte[] salt) {
        return encoder.matches(mix(rawPassword, salt), passwordHash);
    }

    private CharSequence mix(String rawPassword, byte[] salt) {
        StringBuilder sb = new StringBuilder(rawPassword.length() + 64);
        sb.append(rawPassword);
        sb.append(Base64.getEncoder().encodeToString(salt));
        sb.append(Base64.getEncoder().encodeToString(pepper));
        return sb;
    }

    public record Hashed(String hash, byte[] salt) {}
}
