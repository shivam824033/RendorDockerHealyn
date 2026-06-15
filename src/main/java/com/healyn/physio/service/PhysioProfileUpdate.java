package com.healyn.physio.service;

/// The editable fields of the physiotherapist profile. A null field is left
/// unchanged; a blank field clears the stored value (mirrors {@code PatientUpdate}).
public record PhysioProfileUpdate(
        String displayName,
        String qualification,
        Integer experienceYears,
        String specialization,
        String bio,
        String clinicName,
        String clinicAddress,
        String clinicContactPhone,
        String clinicDescription,
        String instagramUrl,
        String facebookUrl,
        String linkedinUrl,
        String websiteUrl) {
}
