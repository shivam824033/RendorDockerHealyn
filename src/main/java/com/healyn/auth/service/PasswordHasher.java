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
    private static final int MEMORY_KB = 4 * 1024;
    private static final int ITERATIONS = 2;

    private final SecureRandom random = new SecureRandom();
    private final Argon2PasswordEncoder encoder =
            new Argon2PasswordEncoder(SALT_BYTES, HASH_LENGTH, PARALLELISM, MEMORY_KB, ITERATIONS);
    private final byte[] pepper;
    private final byte[] decoySalt;
    private final String decoyHash;

    public PasswordHasher(AuthProperties.Password props) {
        String configured = props.pepper();
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("healyn.password.pepper is not configured");
        }
        this.pepper = configured.getBytes(StandardCharsets.UTF_8);
        // Precomputed decoy so a login for a non-existent account can run the same Argon2 work
        // as a real one — see matchesDecoy / audit M1.
        this.decoySalt = new byte[SALT_BYTES];
        random.nextBytes(decoySalt);
        byte[] decoy = new byte[HASH_LENGTH];
        random.nextBytes(decoy);
        this.decoyHash = encoder.encode(mix(Base64.getEncoder().encodeToString(decoy), decoySalt));
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

    /// Runs a full Argon2 verification against a fixed decoy hash and always returns false.
    /// Called when no account matches the login identifier so the response time matches the
    /// real "wrong password" path, denying an attacker an account-existence timing oracle (M1).
    public boolean matchesDecoy(String rawPassword) {
        return encoder.matches(mix(rawPassword == null ? "" : rawPassword, decoySalt), decoyHash);
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
