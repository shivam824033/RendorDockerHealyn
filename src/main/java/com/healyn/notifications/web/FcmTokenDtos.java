package com.healyn.notifications.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class FcmTokenDtos {

    private FcmTokenDtos() {}

    public record RegisterRequest(
            @NotBlank String token,
            @Size(max = 16) String platform,
            @Size(max = 128) String deviceId) {}

    public record RegisterResponse(UUID id) {}
}
