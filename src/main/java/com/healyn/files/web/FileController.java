package com.healyn.files.web;

import com.healyn.auth.domain.AccountRole;
import com.healyn.common.pagination.CursorPage;
import com.healyn.files.domain.FileObject;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
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
                body.patientId(), body.appointmentId(), body.kind(), body.context(),
                body.uploadSource(), body.mimeType(), body.sizeBytes(), body.originalFilename()));
        return FileMapper.toPresignView(result);
    }

    /** A patient's library documents, filtered by uploader (PATIENT / PHYSIO), cursor-paginated. */
    @GetMapping
    public FileDtos.DocumentPage listDocuments(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam("patient_id") UUID patientId,
            @RequestParam("uploader") FileDtos.DocumentUploader uploader,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "limit", required = false, defaultValue = "20") int limit) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        AccountRole role = roleOf(jwt);
        CursorPage<FileObject> page = service.listDocuments(actorId, role, patientId, uploader.role(), cursor, limit);
        Map<UUID, String> numbers = service.appointmentNumbersFor(
                page.items().stream().map(FileObject::getAppointmentId).toList());
        List<FileDtos.FileDocumentView> views = page.items().stream()
                .map(f -> FileMapper.toDocumentView(f,
                        f.getAppointmentId() == null ? null : numbers.get(f.getAppointmentId())))
                .toList();
        return new FileDtos.DocumentPage(views, page.nextCursor());
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
