package com.healyn.files.web;

import com.healyn.auth.domain.AccountRole;
import com.healyn.files.service.FileService;
import com.healyn.files.service.PresignFileRequest;
import com.healyn.files.service.PresignResult;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/files")
public class FileController {

    private final FileService service;

    public FileController(FileService service) {
        this.service = service;
    }

    @PostMapping("/presign")
    public FileDtos.PresignView presign(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody FileDtos.PresignBody body) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        AccountRole role = roleOf(jwt);
        PresignResult result = service.presign(actorId, role, new PresignFileRequest(
                body.patientId(), body.appointmentId(), body.kind(),
                body.mimeType(), body.sizeBytes(), body.originalFilename()));
        return FileMapper.toPresignView(result);
    }

    @PostMapping("/{id}/complete")
    public FileDtos.FileView complete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("id") UUID id) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        AccountRole role = roleOf(jwt);
        return FileMapper.toView(service.complete(actorId, role, id));
    }

    @GetMapping("/{id}/download")
    public FileDtos.DownloadView download(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("id") UUID id) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        AccountRole role = roleOf(jwt);
        var result = service.download(actorId, role, id);
        return new FileDtos.DownloadView(result.url(), result.expiresInSeconds());
    }

    @GetMapping("/{id}")
    public FileDtos.FileView get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("id") UUID id) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        AccountRole role = roleOf(jwt);
        return FileMapper.toView(service.get(actorId, role, id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("id") UUID id) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        AccountRole role = roleOf(jwt);
        service.delete(actorId, role, id);
    }

    private static AccountRole roleOf(Jwt jwt) {
        String role = jwt.getClaimAsString("role");
        return role == null ? AccountRole.ROLE_ACCOUNT : AccountRole.valueOf(role);
    }
}
