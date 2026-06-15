package com.healyn.physio.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class PhysioDtos {

    private PhysioDtos() {}

    /// Common pattern for social/website links: http(s) URL, no whitespace. Blank is
    /// allowed by sending an empty string (clears the field) — the pattern only applies
    /// to non-empty input via the regex's optional whole match.
    private static final String URL_PATTERN = "^$|^https?://\\S+$";

    /// `PATCH /physio/profile`. Every field is optional; a null field is left unchanged
    /// and a blank string clears it. Validation enforces URL format and length caps
    /// (CLAUDE.md / backend validation requirements).
    public record UpdateProfileRequest(
            @Size(max = 160) String displayName,
            @Size(max = 160) String qualification,
            @Min(0) @Max(80) Integer experienceYears,
            @Size(max = 160) String specialization,
            @Size(max = 4000) String bio,
            @Size(max = 160) String clinicName,
            @Size(max = 4000) String clinicAddress,
            @Pattern(regexp = "^$|^\\+[1-9]\\d{6,14}$", message = "E164") @Size(max = 20) String clinicContactPhone,
            @Size(max = 4000) String clinicDescription,
            @Pattern(regexp = URL_PATTERN, message = "URL") @Size(max = 512) String instagramUrl,
            @Pattern(regexp = URL_PATTERN, message = "URL") @Size(max = 512) String facebookUrl,
            @Pattern(regexp = URL_PATTERN, message = "URL") @Size(max = 512) String linkedinUrl,
            @Pattern(regexp = URL_PATTERN, message = "URL") @Size(max = 512) String websiteUrl) {}

    /// `GET /physio/profile`. All fields nullable; [avatarUrl] is a short-lived presigned
    /// GET URL (null when no avatar is set).
    public record ProfileView(
            String displayName,
            String qualification,
            Integer experienceYears,
            String specialization,
            String bio,
            String clinicName,
            String clinicAddress,
            String clinicContactPhone,
            String clinicDescription,
            String instagramUrl,
            String facebookUrl,
            String linkedinUrl,
            String websiteUrl,
            String avatarUrl) {}

    public record AvatarPresignRequest(
            @NotBlank String mimeType,
            @Positive long sizeBytes) {}

    public record AvatarPresignView(
            String objectKey,
            String url,
            String contentType,
            long expiresInSeconds) {}

    public record AvatarConfirmRequest(
            @NotBlank String objectKey,
            @NotBlank String mimeType) {}
}
