package com.healyn.patients.web;

import com.healyn.patients.domain.AccountAddress;
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
                toAddressView(pl.address()),
                link != null ? link.getRelationship() : null,
                link != null ? link.isPrimary() : null,
                link != null ? link.isCanManage() : null,
                p.getCreatedAt(),
                p.getUpdatedAt());
    }

    static PatientDtos.AddressView toAddressView(AccountAddress a) {
        if (a == null) return null;
        return new PatientDtos.AddressView(
                a.getLine1(), a.getLine2(), a.getCity(),
                a.getState(), a.getPostalCode(), a.getCountry());
    }
}
