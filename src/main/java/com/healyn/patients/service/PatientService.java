package com.healyn.patients.service;

import com.healyn.auth.domain.Account;
import com.healyn.auth.domain.AccountRole;
import com.healyn.common.error.ErrorCode;
import com.healyn.common.error.NotFoundException;
import com.healyn.common.error.UnprocessableException;
import com.healyn.common.id.UuidV7;
import com.healyn.common.pagination.Cursor;
import com.healyn.common.pagination.CursorPage;
import com.healyn.patients.domain.AccountAddress;
import com.healyn.patients.domain.AccountPatient;
import com.healyn.patients.domain.Patient;
import com.healyn.patients.domain.PatientRelationship;
import com.healyn.patients.policy.AccessMode;
import com.healyn.patients.policy.PatientAccessPolicy;
import com.healyn.patients.port.ConsentRecorderPort;
import com.healyn.patients.repository.AccountPatientRepository;
import com.healyn.patients.repository.PatientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class PatientService {

    // A roster search needs at least two characters: a single letter would match almost
    // the whole practice (mirrors AppointmentService's global-search floor).
    private static final int ROSTER_MIN_QUERY_LENGTH = 2;
    private static final int ROSTER_DEFAULT_LIMIT = 20;
    private static final int ROSTER_MAX_LIMIT = 50;

    private final PatientRepository patients;
    private final AccountPatientRepository links;
    private final PatientAccessPolicy policy;
    private final AccountAddressService addresses;
    private final ConsentRecorderPort consents;

    public PatientService(PatientRepository patients, AccountPatientRepository links,
                          PatientAccessPolicy policy, AccountAddressService addresses,
                          ConsentRecorderPort consents) {
        this.patients = patients;
        this.links = links;
        this.policy = policy;
        this.addresses = addresses;
        this.consents = consents;
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
                                   NewPatientProfile profile, boolean authorityAttested,
                                   String ipAddress, String userAgent) {
        if (relationship == PatientRelationship.SELF) {
            throw new UnprocessableException(ErrorCode.UNPROCESSABLE,
                    "Family member relationship cannot be SELF");
        }
        if (!authorityAttested) {
            // Managing another person's health data requires the account holder's attested
            // authority (guardian / authorised representative) — DPDP Act 2023.
            throw new UnprocessableException(ErrorCode.PATIENTS_AUTHORITY_REQUIRED,
                    "Authority to manage this family member's health data must be attested");
        }
        Patient patient = newPatient(profile);
        patients.save(patient);
        links.save(new AccountPatient(accountId, patient.getId(), relationship, false, true));
        // Record the family-member authority consent in the same transaction so a managed
        // patient never exists without its authority attestation.
        consents.recordFamilyAuthority(accountId, patient.getId(), ipAddress, userAgent);
        return patient;
    }

    /// Right-to-erasure: redacts identifying and health PII from every patient managed solely
    /// by this account and removes the account's links. A patient still managed by another
    /// account (cross-account sharing) keeps its identity; only this account's link is dropped.
    /// The account's household address is deleted. Clinical records that reference the patients
    /// are retained, now de-identified (Hard Rule #7).
    @Transactional
    public void anonymizeAccountPatients(UUID accountId, Instant when) {
        for (Patient p : links.findActivePatientsForAccount(accountId)) {
            links.findLink(accountId, p.getId()).ifPresent(links::delete);
            if (links.countLinksForPatient(p.getId()) == 0) {
                p.anonymize(when);
            }
        }
        addresses.deleteForAccount(accountId);
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

    /// The physiotherapist's patient roster (F1.16): every live patient, newest-first,
    /// cursor-paginated. An optional [q] of ≥2 characters narrows by Patient ID prefix
    /// (PAT-NNNNNN) or full-name substring; a shorter term is treated as no search.
    /// Household addresses for the page are resolved in one batched query.
    @Transactional(readOnly = true)
    public CursorPage<PatientWithLink> roster(String cursorToken, String q, int limit) {
        int capped = (limit <= 0 || limit > ROSTER_MAX_LIMIT) ? ROSTER_DEFAULT_LIMIT : limit;
        String term = q == null ? "" : q.trim();
        boolean filterSearch = term.length() >= ROSTER_MIN_QUERY_LENGTH;
        // Identifiers are stored upper-case; upper-case the term so a case-sensitive LIKE hits
        // the text_pattern_ops index. Names use ILIKE (the trigram index is case-folding).
        String escaped = escapeLike(term);
        String numberPrefix = escaped.toUpperCase(Locale.ROOT) + "%";
        String nameContains = "%" + escaped + "%";

        // Over-fetch one row to learn whether a further page exists without a count query.
        int fetch = capped + 1;
        List<Patient> rows;
        if (cursorToken == null || cursorToken.isBlank()) {
            rows = patients.rosterFirstPage(filterSearch, numberPrefix, nameContains, fetch);
        } else {
            Cursor c = Cursor.decode(cursorToken);
            rows = patients.rosterAfterCursor(
                    filterSearch, numberPrefix, nameContains, c.pivot(), c.id(), fetch);
        }

        String nextCursor = null;
        if (rows.size() > capped) {
            Patient pivot = rows.get(capped - 1);
            nextCursor = new Cursor(pivot.getCreatedAt(), pivot.getId()).encode();
            rows = rows.subList(0, capped);
        }

        Map<UUID, AccountAddress> byPatient = addresses.findForPatients(
                rows.stream().map(Patient::getId).toList());
        List<PatientWithLink> items = rows.stream()
                .map(p -> new PatientWithLink(p, null, byPatient.get(p.getId())))
                .toList();
        return new CursorPage<>(new ArrayList<>(items), nextCursor);
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

    /// Escapes the LIKE/ILIKE wildcards (`%`, `_`) and the escape char so a typed term is
    /// matched literally — a user typing "%" searches for a percent sign, not "match anything".
    private static String escapeLike(String term) {
        return term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /// A patient with the caller's link to it (null for the physiotherapist) and
    /// the household address surfaced on the profile (null when unset).
    public record PatientWithLink(Patient patient, AccountPatient link, AccountAddress address) {}
}
