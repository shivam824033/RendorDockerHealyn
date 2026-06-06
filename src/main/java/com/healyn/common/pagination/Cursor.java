package com.healyn.common.pagination;

import com.healyn.common.error.ErrorCode;
import com.healyn.common.error.UnprocessableException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;

public record Cursor(Instant pivot, UUID id) {

    public String encode() {
        String raw = pivot.toString() + "|" + id.toString();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static Cursor decode(String token) {
        if (token == null || token.isBlank()) {
            throw invalid();
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            int sep = raw.indexOf('|');
            if (sep <= 0 || sep == raw.length() - 1) throw invalid();
            return new Cursor(
                    Instant.parse(raw.substring(0, sep)),
                    UUID.fromString(raw.substring(sep + 1)));
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw invalid();
        }
    }

    private static UnprocessableException invalid() {
        return new UnprocessableException(ErrorCode.COMMON_INVALID_CURSOR, "Cursor is malformed.");
    }
}
