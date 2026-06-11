package com.healyn.patients.web;

import com.healyn.patients.domain.AccountPatient;
import com.healyn.patients.domain.Patient;
import com.healyn.patients.service.PatientService.PatientWithLink;

final class PatientMapper {

    private PatientMapper() {}

    static PatientDtos.PatientView toView(PatientWithLink pl) {
        Patient p = pl.patient();
        AccountPatient link = pl.link();
        return new PatientDtos.PatientView(
                p.getId(),
                p.getPatientNumber(),
                p.getFullName(),
                p.getDateOfBirth(),
                p.getSex(),
                p.getPhoneE164(),
                p.getEmail(),
                p.getBloodGroup(),
                p.getAllergies(),
                p.getNotes(),
                link != null ? link.getRelationship() : null,
                link != null ? link.isPrimary() : null,
                link != null ? link.isCanManage() : null,
                p.getCreatedAt(),
                p.getUpdatedAt());
    }
}
