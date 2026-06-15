package com.healyn.files.domain;

import java.util.Optional;

/**
 * The closed set of accepted upload types (FILE_STORAGE_GUIDELINES §1). Each value
 * binds the canonical MIME string, the canonical extension, a per-type size cap,
 * and the leading magic bytes used for server-side content verification.
 */
public enum FileMime {

    PDF("application/pdf", "pdf", 20L * 1024 * 1024,
            new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D}, null, 0),              // "%PDF-"
    JPEG("image/jpeg", "jpg", 10L * 1024 * 1024,
            new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, null, 0),
    PNG("image/png", "png", 10L * 1024 * 1024,
            new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}, null, 0),
    // WEBP is a RIFF container: bytes 0-3 = "RIFF", a 4-byte size, then "WEBP" at offset 8.
    // The leading "RIFF" alone also matches WAV/AVI, so the FourCC at offset 8 is verified too.
    WEBP("image/webp", "webp", 10L * 1024 * 1024,
            new byte[]{0x52, 0x49, 0x46, 0x46},                              // "RIFF"
            new byte[]{0x57, 0x45, 0x42, 0x50}, 8);                          // "WEBP" @ 8

    private final String mimeType;
    private final String extension;
    private final long maxBytes;
    private final byte[] magic;
    /// Optional secondary signature verified at {@link #secondaryMagicOffset} (null = none).
    private final byte[] secondaryMagic;
    private final int secondaryMagicOffset;

    FileMime(String mimeType, String extension, long maxBytes, byte[] magic,
             byte[] secondaryMagic, int secondaryMagicOffset) {
        this.mimeType = mimeType;
        this.extension = extension;
        this.maxBytes = maxBytes;
        this.magic = magic;
        this.secondaryMagic = secondaryMagic;
        this.secondaryMagicOffset = secondaryMagicOffset;
    }

    public String mimeType() { return mimeType; }
    public String extension() { return extension; }
    public long maxBytes() { return maxBytes; }

    public static Optional<FileMime> fromMimeType(String value) {
        if (value == null) return Optional.empty();
        String v = value.trim();
        for (FileMime m : values()) {
            if (m.mimeType.equalsIgnoreCase(v)) return Optional.of(m);
        }
        return Optional.empty();
    }

    /** True when {@code head} begins with this type's magic-byte signature (and, where
     * defined, also matches the secondary signature at its offset — e.g. WEBP's FourCC). */
    public boolean matchesMagic(byte[] head) {
        if (head == null || head.length < magic.length) return false;
        for (int i = 0; i < magic.length; i++) {
            if (head[i] != magic[i]) return false;
        }
        if (secondaryMagic != null) {
            if (head.length < secondaryMagicOffset + secondaryMagic.length) return false;
            for (int i = 0; i < secondaryMagic.length; i++) {
                if (head[secondaryMagicOffset + i] != secondaryMagic[i]) return false;
            }
        }
        return true;
    }
}
