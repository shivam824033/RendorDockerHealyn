package com.healyn.patients.web;

import com.healyn.patients.domain.PatientRelationship;
import com.healyn.patients.domain.PatientSex;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class PatientDtos {

    private PatientDtos() {}

    public record PrimaryProfileRequest(
            @NotBlank @Size(max = 160) String fullName,
            @NotNull @Past LocalDate dateOfBirth,
            PatientSex sex) {}

    public record CreateFamilyMemberRequest(
            @NotBlank @Size(max = 160) String fullName,
            @NotNull @Past LocalDate dateOfBirth,
            PatientSex sex,
            @NotNull PatientRelationship relationship,
            @Pattern(regexp = "^\\+[1-9]\\d{6,14}$", message = "E164") @Size(max = 20) String phoneE164,
            @Email @Size(max = 254) String email,
            @Size(max = 3) String bloodGroup,
            @Size(max = 4000) String allergies,
            @Size(max = 4000) String notes) {}

    public record UpdatePatientRequest(
            @Size(max = 160) String fullName,
            @Past LocalDate dateOfBirth,
            PatientSex sex,
            @Pattern(regexp = "^\\+[1-9]\\d{6,14}$", message = "E164") @Size(max = 20) String phoneE164,
            @Email @Size(max = 254) String email,
            @Size(max = 3) String bloodGroup,
            @Size(max = 4000) String allergies,
            @Size(max = 4000) String notes) {}

    public record PatientView(
            UUID id,
            String patientNumber,
            String fullName,
            LocalDate dateOfBirth,
            PatientSex sex,
            String phoneE164,
            String email,
            String bloodGroup,
            String allergies,
            String notes,
            PatientRelationship relationship,
            Boolean primary,
            Boolean canManage,
            Instant createdAt,
            Instant updatedAt) {}

    public record PatientListResponse(List<PatientView> patients) {}
}
