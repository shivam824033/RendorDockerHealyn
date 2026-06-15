package com.healyn.files.domain;

/**
 * Why a file exists. {@link #DISCUSSION} files are attachments on a discussion
 * message; {@link #LIBRARY} files are documents uploaded directly into a
 * patient's document library. The library listing filters to {@code LIBRARY} so
 * chat attachments never appear as documents.
 */
public enum FileContext {
    DISCUSSION,
    LIBRARY
}
