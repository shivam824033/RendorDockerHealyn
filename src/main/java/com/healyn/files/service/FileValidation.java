package com.healyn.files.service;

/** Filename sanitization for display (FILE_STORAGE_GUIDELINES §8). Never used for storage keys. */
public final class FileValidation {

    private static final int MAX_FILENAME_LENGTH = 255;

    private FileValidation() {}

    /**
     * Strips path components and control characters, forbids leading dots, and truncates
     * to 255 chars. Returns null/blank-equivalent input as an empty string so callers can
     * reject it uniformly.
     */
    public static String sanitizeFilename(String raw) {
        if (raw == null) return "";
        String name = raw.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);

        StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c >= 0x20 && c != 0x7F) sb.append(c);
        }
        name = sb.toString().strip();

        int firstNonDot = 0;
        while (firstNonDot < name.length() && name.charAt(firstNonDot) == '.') firstNonDot++;
        name = name.substring(firstNonDot);

        if (name.length() > MAX_FILENAME_LENGTH) name = name.substring(0, MAX_FILENAME_LENGTH);
        return name;
    }

    public static boolean isUsableFilename(String sanitized) {
        return sanitized != null && !sanitized.isBlank();
    }
}
