package com.healyn.treatmentnotes.service;

import java.time.Instant;

public record UpsertTreatmentNoteRequest(
        String diagnosis,
        String notes,
        String recoveryInstructions,
        Instant nextReviewAt) {
}
