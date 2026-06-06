package com.healyn.files.service;

import com.healyn.files.domain.FileObject;
import com.healyn.files.repository.FileObjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Persists a QUARANTINED verdict in its own transaction ({@link Propagation#REQUIRES_NEW}).
 * Validation failures during {@code complete} reject the request by throwing, which would roll
 * back the caller's transaction; quarantining must survive that rollback so a file flagged as
 * malicious cannot be re-completed and stays on record. A no-op if the file is already gone.
 */
@Service
public class FileQuarantineService {

    private final FileObjectRepository files;

    public FileQuarantineService(FileObjectRepository files) {
        this.files = files;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void quarantine(UUID fileId) {
        files.findByIdAndDeletedAtIsNull(fileId).ifPresent(FileObject::quarantine);
    }
}
