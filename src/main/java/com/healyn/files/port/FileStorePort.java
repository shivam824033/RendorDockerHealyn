package com.healyn.files.port;

import java.time.Duration;
import java.util.Optional;

/**
 * The storage edge for file bytes. The application never streams upload/download
 * bytes itself (FILE_STORAGE_GUIDELINES §11) — it hands out presigned URLs and only
 * reads objects server-side for validation. Implemented by {@code MinioFileStore};
 * tests use an in-memory fake.
 */
public interface FileStorePort {

    /** Presigned PUT URL the client uploads to directly. */
    String presignPut(String key, String contentType, Duration ttl);

    /** Presigned GET URL with a Content-Disposition attachment filename. */
    String presignGet(String key, String downloadFilename, Duration ttl);

    /** Size of the stored object in bytes, or empty if no object exists at the key. */
    Optional<Long> objectSize(String key);

    /** Read up to {@code maxBytes} of the object for server-side validation. */
    byte[] read(String key, long maxBytes);

    void delete(String key);
}
