package com.healyn.audit.domain;

/** Canonical {@code resource_type} values for audit rows. */
public final class AuditResource {

    public static final String APPOINTMENT = "appointment";
    public static final String FILE = "file";
    public static final String DISCUSSION_MESSAGE = "discussion_message";
    public static final String TREATMENT_NOTE = "treatment_note";
    public static final String PATIENT = "patient";

    private AuditResource() {}
}
