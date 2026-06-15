package com.healyn.physio.web;

import com.healyn.auth.domain.AccountRole;
import com.healyn.physio.domain.PhysioProfile;
import com.healyn.physio.service.AvatarPresign;
import com.healyn.physio.service.PhysioProfileService;
import com.healyn.physio.service.PhysioProfileUpdate;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/// The single physiotherapist's profile. {@code GET} is open to any authenticated
/// account (every patient is shown the one physiotherapist); the mutating endpoints
/// are guarded in the service via {@code PhysioProfilePolicy} (CLAUDE.md hard rule #2).
@RestController
@RequestMapping("/physio/profile")
public class PhysioController {

    private final PhysioProfileService service;

    public PhysioController(PhysioProfileService service) {
        this.service = service;
    }

    @GetMapping
    public PhysioDtos.ProfileView get() {
        PhysioProfile profile = service.find().orElse(null);
        String avatarUrl = profile == null ? null : service.avatarUrl(profile).orElse(null);
        return PhysioMapper.toView(profile, avatarUrl);
    }

    @PatchMapping
    public PhysioDtos.ProfileView update(@AuthenticationPrincipal Jwt jwt,
                                         @Valid @RequestBody PhysioDtos.UpdateProfileRequest body) {
        UUID accountId = UUID.fromString(jwt.getSubject());
        PhysioProfile profile = service.update(accountId, roleOf(jwt), new PhysioProfileUpdate(
                body.displayName(), body.qualification(), body.experienceYears(), body.specialization(),
                body.bio(), body.clinicName(), body.clinicAddress(), body.clinicContactPhone(),
                body.clinicDescription(), body.instagramUrl(), body.facebookUrl(),
                body.linkedinUrl(), body.websiteUrl()));
        return PhysioMapper.toView(profile, service.avatarUrl(profile).orElse(null));
    }

    @PostMapping("/avatar/presign")
    public PhysioDtos.AvatarPresignView presignAvatar(@AuthenticationPrincipal Jwt jwt,
                                                      @Valid @RequestBody PhysioDtos.AvatarPresignRequest body) {
        UUID accountId = UUID.fromString(jwt.getSubject());
        AvatarPresign p = service.presignAvatar(accountId, roleOf(jwt), body.mimeType(), body.sizeBytes());
        return new PhysioDtos.AvatarPresignView(p.objectKey(), p.url(), p.contentType(), p.expiresInSeconds());
    }

    @PostMapping("/avatar/confirm")
    public PhysioDtos.ProfileView confirmAvatar(@AuthenticationPrincipal Jwt jwt,
                                                @Valid @RequestBody PhysioDtos.AvatarConfirmRequest body) {
        UUID accountId = UUID.fromString(jwt.getSubject());
        PhysioProfile profile = service.confirmAvatar(accountId, roleOf(jwt), body.objectKey(), body.mimeType());
        return PhysioMapper.toView(profile, service.avatarUrl(profile).orElse(null));
    }

    private static AccountRole roleOf(Jwt jwt) {
        String role = jwt.getClaimAsString("role");
        return role == null ? AccountRole.ROLE_ACCOUNT : AccountRole.valueOf(role);
    }
}
