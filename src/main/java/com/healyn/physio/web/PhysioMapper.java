package com.healyn.physio.web;

import com.healyn.physio.domain.PhysioProfile;

final class PhysioMapper {

    private PhysioMapper() {}

    /// Maps the profile to its view. [avatarUrl] is a freshly presigned GET URL (or null).
    /// A null [profile] yields an all-null view so a patient sees an empty card rather
    /// than a 404 before the physiotherapist has filled anything in.
    static PhysioDtos.ProfileView toView(PhysioProfile profile, String avatarUrl) {
        if (profile == null) {
            return new PhysioDtos.ProfileView(
                    null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        }
        return new PhysioDtos.ProfileView(
                profile.getDisplayName(),
                profile.getQualification(),
                profile.getExperienceYears(),
                profile.getSpecialization(),
                profile.getBio(),
                profile.getClinicName(),
                profile.getClinicAddress(),
                profile.getClinicContactPhone(),
                profile.getClinicDescription(),
                profile.getInstagramUrl(),
                profile.getFacebookUrl(),
                profile.getLinkedinUrl(),
                profile.getWebsiteUrl(),
                avatarUrl);
    }
}
