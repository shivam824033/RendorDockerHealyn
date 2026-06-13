package com.healyn.patients.service;

import com.healyn.patients.domain.AccountAddress;
import com.healyn.patients.repository.AccountAddressRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/// Owns the account's household postal address: capture at registration, edit
/// from the profile, and resolution per patient for the physiotherapist's view.
/// See docs/PATIENT_RELATIONSHIP_MODEL.md and docs/DATABASE_SCHEMA.md §3.5a.
@Service
public class AccountAddressService {

    private final AccountAddressRepository addresses;

    public AccountAddressService(AccountAddressRepository addresses) {
        this.addresses = addresses;
    }

    /// Creates or replaces the household address for an account. Idempotent —
    /// the account id is the key, so a second call overwrites the first.
    @Transactional
    public AccountAddress upsert(UUID accountId, AddressData data) {
        AccountAddress existing = addresses.findById(accountId).orElse(null);
        if (existing == null) {
            return addresses.save(new AccountAddress(accountId,
                    trim(data.line1()), blankToNull(data.line2()), trim(data.city()),
                    trim(data.state()), trim(data.postalCode()), blankToNull(data.country())));
        }
        existing.apply(trim(data.line1()), blankToNull(data.line2()), trim(data.city()),
                trim(data.state()), trim(data.postalCode()), blankToNull(data.country()));
        return existing;
    }

    @Transactional(readOnly = true)
    public Optional<AccountAddress> findForAccount(UUID accountId) {
        return addresses.findById(accountId);
    }

    /// The household address resolved through a patient's managing account(s) —
    /// the physiotherapist's view of where a patient lives. Empty when no
    /// managing account has set one.
    @Transactional(readOnly = true)
    public Optional<AccountAddress> findForPatient(UUID patientId) {
        List<AccountAddress> rows = addresses.findForPatient(patientId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /// Batched {@link #findForPatient} for roster endpoints: patientId → address,
    /// preferring the same row {@code findForPatient} would pick. Patients with no
    /// address are absent from the map.
    @Transactional(readOnly = true)
    public Map<UUID, AccountAddress> findForPatients(Collection<UUID> patientIds) {
        if (patientIds.isEmpty()) return Map.of();
        Map<UUID, AccountAddress> byPatient = new HashMap<>();
        for (Object[] row : addresses.findForPatients(patientIds)) {
            byPatient.putIfAbsent((UUID) row[0], (AccountAddress) row[1]);
        }
        return byPatient;
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
