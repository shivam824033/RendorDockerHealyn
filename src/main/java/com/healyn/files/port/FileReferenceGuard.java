package com.healyn.files.port;

import java.util.UUID;

/**
 * Tells the files module whether a file is still referenced by another clinical
 * resource. Implemented by referencing modules (e.g. discussion) so the files
 * module stays free of inbound dependencies. A referenced file must not be deleted
 * (FILE_STORAGE_GUIDELINES §10).
 */
public interface FileReferenceGuard {

    boolean isReferenced(UUID fileId);
}
