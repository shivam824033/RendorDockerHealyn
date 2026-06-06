package com.healyn.patients.service;

import com.healyn.auth.domain.Account;
import com.healyn.auth.domain.AccountRole;
import com.healyn.common.error.ErrorCode;
import com.healyn.common.error.NotFoundException;
import com.healyn.common.error.UnprocessableException;
import com.healyn.common.id.UuidV7;
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
import java.util.UUID;

@Service
public class PatientService {

    private final PatientRepository patients;
    private final AccountPatientRepository links;
    private final PatientAccessPolicy policy;

    public PatientService(PatientRepository patients, AccountPatientRepository links,
                          PatientAccessPolicy policy) {
        this.patients = patients;
        this.links = links;
        this.policy = policy;
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
            return patients.findAll().stream()
                    .filter(p -> p.getDeletedAt() == null)
                    .map(p -> new PatientWithLink(p, null))
                    .toList();
        }
        return links.findActivePatientsForAccount(accountId).stream()
                .map(p -> new PatientWithLink(p, links.findLink(accountId, p.getId()).orElse(null)))
                .toList();
    }

    @Transactional(readOnly = true)
    public PatientWithLink get(UUID accountId, AccountRole role, UUID patientId) {
        policy.requireAccess(accountId, role, patientId, AccessMode.READ);
        Patient patient = loadAlive(patientId);
        AccountPatient link = role == AccountRole.ROLE_PHYSIO
                ? null
                : links.findLink(accountId, patientId).orElse(null);
        return new PatientWithLink(patient, link);
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

    public record PatientWithLink(Patient patient, AccountPatient link) {}
}
