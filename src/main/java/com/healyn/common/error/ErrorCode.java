package com.healyn.common.error;

public final class ErrorCode {

    public static final String VALIDATION_FAILED = "common.validation_failed";
    public static final String UNAUTHORIZED = "common.unauthorized";
    public static final String FORBIDDEN = "common.forbidden";
    public static final String NOT_FOUND = "common.not_found";
    public static final String CONFLICT = "common.conflict";
    public static final String UNPROCESSABLE = "common.unprocessable";
    public static final String RATE_LIMITED = "common.rate_limited";
    public static final String INTERNAL = "common.internal";
    public static final String IDEMPOTENCY_CONFLICT = "common.idempotency_conflict";

    public static final String PATIENTS_PRIMARY_REQUIRED = "patients.primary_required";
    public static final String PATIENTS_RELATIONSHIP_EXISTS = "patients.relationship_exists";
    public static final String PATIENTS_NOT_FOUND = "patients.not_found";
    public static final String PATIENTS_AUTHORITY_REQUIRED = "patients.authority_required";

    public static final String COMPLIANCE_LEGAL_DOCUMENT_NOT_FOUND = "compliance.legal_document_not_found";
    public static final String COMPLIANCE_DELETION_ALREADY_REQUESTED = "compliance.deletion_already_requested";
    public static final String COMPLIANCE_DELETION_NOT_FOUND = "compliance.deletion_not_found";
    public static final String COMPLIANCE_INVALID_PASSWORD = "compliance.invalid_password";
    public static final String COMPLIANCE_DELETION_NOT_ALLOWED = "compliance.deletion_not_allowed";

    public static final String AVAILABILITY_RULE_NOT_FOUND = "availability.rule_not_found";
    public static final String AVAILABILITY_BLACKOUT_NOT_FOUND = "availability.blackout_not_found";
    public static final String AVAILABILITY_INVALID_TIMEZONE = "availability.invalid_timezone";
    public static final String AVAILABILITY_INVALID_RANGE = "availability.invalid_range";
    public static final String AVAILABILITY_BLACKOUT_OVERLAP = "availability.blackout_overlap";

    public static final String APPOINTMENT_NOT_FOUND = "appointments.not_found";
    public static final String APPOINTMENT_INVALID_TRANSITION = "appointments.invalid_transition";
    public static final String APPOINTMENT_SLOT_UNAVAILABLE = "appointments.slot_unavailable";
    public static final String APPOINTMENT_INVALID_SCHEDULE = "appointments.invalid_schedule";
    public static final String APPOINTMENT_CANCEL_REASON_REQUIRED = "appointments.cancel_reason_required";

    public static final String COMMON_IDEMPOTENCY_KEY_REQUIRED = "common.idempotency_key_required";
    public static final String COMMON_INVALID_CURSOR = "common.invalid_cursor";

    public static final String DISCUSSION_MESSAGE_NOT_FOUND = "discussion.message_not_found";
    public static final String DISCUSSION_APPOINTMENT_TERMINAL = "discussion.appointment_terminal";
    public static final String DISCUSSION_EDIT_WINDOW_EXPIRED = "discussion.edit_window_expired";
    public static final String DISCUSSION_NOT_SENDER = "discussion.not_sender";
    public static final String DISCUSSION_EMPTY_MESSAGE = "discussion.empty_message";
    public static final String DISCUSSION_BODY_TOO_LONG = "discussion.body_too_long";
    public static final String DISCUSSION_TOO_MANY_ATTACHMENTS = "discussion.too_many_attachments";
    public static final String DISCUSSION_ATTACHMENT_NOT_FOUND = "discussion.attachment_not_found";
    public static final String DISCUSSION_ATTACHMENT_NOT_READY = "discussion.attachment_not_ready";
    public static final String DISCUSSION_ATTACHMENT_PATIENT_MISMATCH = "discussion.attachment_patient_mismatch";

    public static final String TREATMENT_NOTE_NOT_FOUND = "treatment_notes.not_found";
    public static final String TREATMENT_NOTE_APPOINTMENT_NOT_COMPLETED = "treatment_notes.appointment_not_completed";
    public static final String TREATMENT_NOTE_EMPTY = "treatment_notes.empty";
    public static final String TREATMENT_NOTE_FIELD_TOO_LONG = "treatment_notes.field_too_long";

    public static final String FILE_NOT_FOUND = "files.not_found";
    public static final String FILE_UNSUPPORTED_MIME = "files.unsupported_mime";
    public static final String FILE_TOO_LARGE = "files.too_large";
    public static final String FILE_KIND_REQUIRED = "files.kind_required";
    public static final String FILE_FILENAME_INVALID = "files.filename_invalid";
    public static final String FILE_PATIENT_MISMATCH = "files.patient_mismatch";
    public static final String FILE_DAILY_CAP_EXCEEDED = "files.daily_cap_exceeded";
    public static final String FILE_INVALID_STATE = "files.invalid_state";
    public static final String FILE_OBJECT_MISSING = "files.object_missing";
    public static final String FILE_MAGIC_BYTE_MISMATCH = "files.magic_byte_mismatch";
    public static final String FILE_REFERENCED = "files.referenced";

    public static final String PHYSIO_FORBIDDEN = "physio.forbidden";
    public static final String PHYSIO_AVATAR_UNSUPPORTED_MIME = "physio.avatar_unsupported_mime";
    public static final String PHYSIO_AVATAR_TOO_LARGE = "physio.avatar_too_large";
    public static final String PHYSIO_AVATAR_KEY_INVALID = "physio.avatar_key_invalid";
    public static final String PHYSIO_AVATAR_INVALID = "physio.avatar_invalid";

    public static final String PROMOTION_FORBIDDEN = "promotions.forbidden";
    public static final String PROMOTION_NOT_FOUND = "promotions.not_found";
    public static final String PROMOTION_LIMIT_REACHED = "promotions.limit_reached";
    public static final String PROMOTION_INVALID_SCHEDULE = "promotions.invalid_schedule";
    public static final String PROMOTION_INVALID_ACTION = "promotions.invalid_action";
    public static final String PROMOTION_REORDER_MISMATCH = "promotions.reorder_mismatch";
    public static final String PROMOTION_COVER_UNSUPPORTED_MIME = "promotions.cover_unsupported_mime";
    public static final String PROMOTION_COVER_TOO_LARGE = "promotions.cover_too_large";
    public static final String PROMOTION_COVER_KEY_INVALID = "promotions.cover_key_invalid";
    public static final String PROMOTION_COVER_INVALID = "promotions.cover_invalid";

    private ErrorCode() {}
}
