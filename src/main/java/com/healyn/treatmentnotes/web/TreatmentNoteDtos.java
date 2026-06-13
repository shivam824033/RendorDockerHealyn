package com.healyn.treatmentnotes.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class TreatmentNoteDtos {

    private TreatmentNoteDtos() {}

    public record UpsertTreatmentNoteBody(
            String diagnosis,
            String notes,
            String recoveryInstructions,
            Instant nextReviewAt) {}

    public record TreatmentNoteView(
            UUID id,
            UUID appointmentId,
            UUID patientId,
            UUID authorAccountId,
            String diagnosis,
            String notes,
            String recoveryInstructions,
            Instant nextReviewAt,
            Instant createdAt,
            Instant updatedAt) {}

    public record TreatmentNotePage(List<TreatmentNoteView> items, String nextCursor) {}

    /// Ask which of these appointments already have a treatment note (physio dashboard aid).
    public record NoteStatusRequest(List<UUID> appointmentIds) {}

    /// The subset of the requested appointment ids that have a note. Anything not listed
    /// still needs one written.
    public record NoteStatusResponse(List<UUID> withNotes) {}
}
