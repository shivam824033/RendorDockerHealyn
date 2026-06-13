package com.healyn.patients.service;

import com.healyn.auth.domain.Account;
import com.healyn.auth.domain.AccountRole;
import com.healyn.common.error.ErrorCode;
import com.healyn.common.error.NotFoundException;
import com.healyn.common.error.UnprocessableException;
import com.healyn.common.id.UuidV7;
import com.healyn.patients.domain.AccountAddress;
import com.healyn.patients.domain.AccountPatient;
import com.healyn.patients.domain.Patient;
import com.healyn.patients.domain.PatientRelationship;
import com.healyn.patients.policy.AccessMode;
import com.healyn.patients.policy.PatientAccessPolicy;
import com.healyn.patients.repository.AccountPatientRepository;
import com.healyn.patients.repository.PatientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PatientService {

    private final PatientRepository patients;
    private final AccountPatientRepository links;
    private final PatientAccessPolicy policy;
    private final AccountAddressService addresses;

    public PatientService(PatientRepository patients, AccountPatientRepository links,
                          PatientAccessPolicy policy, AccountAddressService addresses) {
        this.patients = patients;
        this.links = links;
        this.policy = policy;
        this.addresses = addresses;
    }

    @Transactional
    public Patient createPrimaryFor(Account account, NewPatientProfile profile) {
        Patient patient = newPatient(profile);
        patients.save(patient);
        links.save(new AccountPatient(account.getId(), patient.getId(),
                PatientRelationship.SELF, true, true));
        return patient;
    }

    @Transactional
    public Patient addFamilyMember(UUID accountId, PatientRelationship relationship,
                                   NewPatientProfile profile) {
        if (relationship == PatientRelationship.SELF) {
            throw new UnprocessableException(ErrorCode.UNPROCESSABLE,
                    "Family member relationship cannot be SELF");
        }
        Patient patient = newPatient(profile);
        patients.save(patient);
        links.save(new AccountPatient(accountId, patient.getId(), relationship, false, true));
        return patient;
    }

    @Transactional(readOnly = true)
    public List<PatientWithLink> listForAccount(UUID accountId, AccountRole role) {
        if (role == AccountRole.ROLE_PHYSIO) {
            List<Patient> live = patients.findAll().stream()
                    .filter(p -> p.getDeletedAt() == null)
                    .toList();
            // The physiotherapist sees each patient's household address resolved
            // through its managing account — batched to one query for the roster.
            Map<UUID, AccountAddress> byPatient = addresses.findForPatients(
                    live.stream().map(Patient::getId).toList());
            return live.stream()
                    .map(p -> new PatientWithLink(p, null, byPatient.get(p.getId())))
                    .toList();
        }
        // Patient side: every patient on the account shares the account's own
        // household address — resolved once, not per patient.
        AccountAddress household = addresses.findForAccount(accountId).orElse(null);
        return links.findActivePatientsForAccount(accountId).stream()
                .map(p -> new PatientWithLink(p, links.findLink(accountId, p.getId()).orElse(null), household))
                .toList();
    }

    @Transactional(readOnly = true)
    public PatientWithLink get(UUID accountId, AccountRole role, UUID patientId) {
        policy.requireAccess(accountId, role, patientId, AccessMode.READ);
        Patient patient = loadAlive(patientId);
        if (role == AccountRole.ROLE_PHYSIO) {
            return new PatientWithLink(patient, null, addresses.findForPatient(patientId).orElse(null));
        }
        AccountPatient link = links.findLink(accountId, patientId).orElse(null);
        return new PatientWithLink(patient, link, addresses.findForAccount(accountId).orElse(null));
    }

    @Transactional
    public Patient update(UUID accountId, AccountRole role, UUID patientId, PatientUpdate u) {
        policy.requireAccess(accountId, role, patientId, AccessMode.WRITE);
        Patient patient = loadAlive(patientId);
        if (u.fullName() != null) patient.rename(u.fullName());
        if (u.dateOfBirth() != null) patient.setDateOfBirth(u.dateOfBirth());
        if (u.sex() != null) patient.setSex(u.sex());
        if (u.phoneE164() != null) patient.setPhoneE164(blankToNull(u.phoneE164()));
        if (u.email() != null) patient.setEmail(blankToNull(u.email()));
        if (u.bloodGroup() != null) patient.setBloodGroup(blankToNull(u.bloodGroup()));
        if (u.allergies() != null) patient.setAllergies(blankToNull(u.allergies()));
        if (u.notes() != null) patient.setNotes(blankToNull(u.notes()));
        return patient;
    }

    @Transactional
    public void removeLink(UUID accountId, AccountRole role, UUID patientId) {
        policy.requireAccess(accountId, role, patientId, AccessMode.WRITE);
        AccountPatient link = links.findLink(accountId, patientId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.PATIENTS_NOT_FOUND, "Patient link not found"));
        if (link.isPrimary()) {
            throw new UnprocessableException(ErrorCode.PATIENTS_PRIMARY_REQUIRED,
                    "Cannot remove the account's primary patient");
        }
        links.delete(link);
        if (links.countLinksForPatient(patientId) == 0) {
            patients.findById(patientId).ifPresent(p -> p.softDelete(Instant.now()));
        }
    }

    private Patient loadAlive(UUID patientId) {
        Patient patient = patients.findById(patientId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.PATIENTS_NOT_FOUND, "Patient not found"));
        if (patient.getDeletedAt() != null) {
            throw new NotFoundException(ErrorCode.PATIENTS_NOT_FOUND, "Patient not found");
        }
        return patient;
    }

    private static Patient newPatient(NewPatientProfile p) {
        Patient patient = new Patient(UuidV7.generate(), p.fullName(), p.dateOfBirth(), p.sex());
        patient.setPhoneE164(blankToNull(p.phoneE164()));
        patient.setEmail(blankToNull(p.email()));
        patient.setBloodGroup(blankToNull(p.bloodGroup()));
        patient.setAllergies(blankToNull(p.allergies()));
        patient.setNotes(blankToNull(p.notes()));
        return patient;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    /// A patient with the caller's link to it (null for the physiotherapist) and
    /// the household address surfaced on the profile (null when unset).
    public record PatientWithLink(Patient patient, AccountPatient link, AccountAddress address) {}
}
