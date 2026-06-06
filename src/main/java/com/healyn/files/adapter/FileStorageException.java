package com.healyn.files.adapter;

/** Wraps storage-backend failures so they surface as a 5xx rather than leaking SDK types. */
public class FileStorageException extends RuntimeException {
    public FileStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
