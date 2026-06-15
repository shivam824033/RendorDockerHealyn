package com.healyn.auth.web;

import com.healyn.patients.domain.PatientSex;
import com.healyn.patients.web.PatientDtos;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {}

    public record DeviceRequest(
            @NotBlank @Size(max = 128) String deviceId,
            @Size(max = 64) String deviceLabel,
            @Size(max = 4096) String fcmToken) {}

    public record TargetRequest(
            @Email @Size(max = 254) String email,
            @Pattern(regexp = "^\\+[1-9]\\d{6,14}$", message = "E164") String phone) {

        public boolean hasExactlyOne() {
            return (email == null) ^ (phone == null);
        }
    }

    public record RegisterStartRequest(@Valid @NotNull TargetRequest target) {}

    public record PrimaryPatientProfileRequest(
            @NotBlank @Size(max = 160) String fullName,
            @NotNull @Past LocalDate dateOfBirth,
            PatientSex sex) {}

    /// Consents the account holder must grant at signup. All three are mandatory for lawful
    /// processing of health data (DPDP Act 2023) — each must be true or registration is rejected.
    public record RegistrationConsents(
            @AssertTrue(message = "TERMS_REQUIRED") boolean termsAccepted,
            @AssertTrue(message = "PRIVACY_REQUIRED") boolean privacyAccepted,
            @AssertTrue(message = "HEALTH_DATA_REQUIRED") boolean healthDataProcessingAccepted) {}

    public record RegisterCompleteRequest(
            @NotNull UUID challengeId,
            @NotBlank @Pattern(regexp = "^\\d{6}$", message = "OTP_FORMAT") String code,
            @NotBlank @Size(min = 10, max = 128) String password,
            @Valid @NotNull DeviceRequest device,
            @Valid @NotNull PrimaryPatientProfileRequest profile,
            @Valid @NotNull PatientDtos.AddressRequest address,
            @Valid @NotNull RegistrationConsents consents) {}

    public record LoginRequest(
            @NotBlank @Size(max = 254) String emailOrPhone,
            @NotBlank @Size(max = 128) String password,
            @Valid @NotNull DeviceRequest device) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    public record PasswordResetStartRequest(@Valid @NotNull TargetRequest target) {}

    public record PasswordResetCompleteRequest(
            @NotNull UUID challengeId,
            @NotBlank @Pattern(regexp = "^\\d{6}$", message = "OTP_FORMAT") String code,
            @NotBlank @Size(min = 10, max = 128) String newPassword) {}

    public record ChallengeResponse(UUID challengeId) {}

    public record TokenResponse(
            UUID sessionId,
            String accessToken,
            Instant accessTokenExpiresAt,
            String refreshToken,
            Instant refreshTokenExpiresAt) {}

    public record SessionView(
            UUID id,
            String deviceId,
            String deviceLabel,
            Instant issuedAt,
            Instant lastSeenAt,
            Instant expiresAt) {}

    public record SessionListResponse(List<SessionView> sessions) {}
}
