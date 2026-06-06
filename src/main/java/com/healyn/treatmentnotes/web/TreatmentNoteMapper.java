package com.healyn.treatmentnotes.web;

import com.healyn.treatmentnotes.domain.TreatmentNote;

public final class TreatmentNoteMapper {

    private TreatmentNoteMapper() {}

    public static TreatmentNoteDtos.TreatmentNoteView toView(TreatmentNote n) {
        return new TreatmentNoteDtos.TreatmentNoteView(
                n.getId(),
                n.getAppointmentId(),
                n.getPatientId(),
                n.getAuthorAccountId(),
                n.getDiagnosis(),
                n.getNotes(),
                n.getRecoveryInstructions(),
                n.getNextReviewAt(),
                n.getCreatedAt(),
                n.getUpdatedAt());
    }
}
