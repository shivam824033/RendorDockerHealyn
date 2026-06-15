package com.healyn.patients.web;

import com.healyn.patients.domain.PatientRelationship;
import com.healyn.patients.domain.PatientSex;
import jakarta.validation.constraints.AssertTrue;
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

    /// Household postal address as sent by the client. Shared by registration
    /// (docs/API_STANDARDS.md §9.1) and `PUT /account/address` (§9.2). [line2] is
    /// optional; [country] defaults to "India" server-side when blank.
    public record AddressRequest(
            @NotBlank @Size(max = 160) String line1,
            @Size(max = 160) String line2,
            @NotBlank @Size(max = 80) String city,
            @NotBlank @Size(max = 80) String state,
            @NotBlank @Size(max = 16) String postalCode,
            @Size(max = 60) String country) {}

    public record AddressView(
            String line1,
            String line2,
            String city,
            String state,
            String postalCode,
            String country) {}

    /// `GET /account/address` — [address] is null when the household has none set.
    public record AccountAddressResponse(AddressView address) {}

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
            @Size(max = 4000) String notes,
            // The account holder must attest authority to manage this person's health data
            // (guardian / authorised representative) — DPDP Act 2023. Must be true.
            @AssertTrue(message = "AUTHORITY_REQUIRED") boolean authorityAttested) {}

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
            AddressView address,
            PatientRelationship relationship,
            Boolean primary,
            Boolean canManage,
            Instant createdAt,
            Instant updatedAt) {}

    /// The patient list. For the patient side this is the account's family roster
    /// ([nextCursor] is always null — the family is small). For the physiotherapist it
    /// is one cursor page of the practice roster; [nextCursor] is null on the last page.
    public record PatientListResponse(List<PatientView> patients, String nextCursor) {}
}
