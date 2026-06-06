package com.healyn.patients.service;

import com.healyn.patients.domain.PatientSex;

import java.time.LocalDate;

public record PatientUpdate(
        String fullName,
        LocalDate dateOfBirth,
        PatientSex sex,
        String phoneE164,
        String email,
        String bloodGroup,
        String allergies,
        String notes) {
}
