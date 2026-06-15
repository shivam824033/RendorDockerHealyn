package com.healyn.auth.service;

import com.healyn.auth.domain.Account;
import com.healyn.auth.domain.AccountRole;
import com.healyn.auth.domain.OtpChallenge;
import com.healyn.auth.domain.OtpChannel;
import com.healyn.auth.domain.OtpPurpose;
import com.healyn.auth.port.RegistrationConsentRecorder;
import com.healyn.auth.repository.AccountRepository;
import com.healyn.common.error.ConflictException;
import com.healyn.common.error.ErrorCode;
import com.healyn.common.id.UuidV7;
import com.healyn.patients.service.AccountAddressService;
import com.healyn.patients.service.AddressData;
import com.healyn.patients.service.NewPatientProfile;
import com.healyn.patients.service.PatientService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class RegistrationService {

    private final AccountRepository accounts;
    private final OtpService otp;
    private final PasswordHasher passwordHasher;
    private final DeviceSessionService sessions;
    private final PatientService patients;
    private final AccountAddressService addresses;
    private final RegistrationConsentRecorder consents;
    private final PasswordPolicy passwordPolicy;

    public RegistrationService(AccountRepository accounts, OtpService otp,
                               PasswordHasher passwordHasher, DeviceSessionService sessions,
                               PatientService patients, AccountAddressService addresses,
                               RegistrationConsentRecorder consents, PasswordPolicy passwordPolicy) {
        this.accounts = accounts;
        this.otp = otp;
        this.passwordHasher = passwordHasher;
        this.sessions = sessions;
        this.patients = patients;
        this.addresses = addresses;
        this.consents = consents;
        this.passwordPolicy = passwordPolicy;
    }

    @Transactional
    public UUID start(String email, String phone) {
        String target = email != null ? email.toLowerCase() : phone;
        OtpChannel channel = email != null ? OtpChannel.EMAIL : OtpChannel.SMS;
        return otp.issue(target, channel, OtpPurpose.REGISTRATION, null);
    }

    @Transactional
    public IssuedSession complete(UUID challengeId, String code, String rawPassword,
                                  DeviceMeta device, NewPatientProfile primaryProfile,
                                  AddressData address) {
        OtpChallenge challenge = otp.verify(challengeId, code, OtpPurpose.REGISTRATION);
        boolean isEmail = challenge.getChannel() == OtpChannel.EMAIL;
        String target = challenge.getTarget();

        if (isEmail && accounts.existsByEmail(target)) {
            throw new ConflictException(ErrorCode.CONFLICT, "Account already exists");
        }
        if (!isEmail && accounts.existsByPhoneE164(target)) {
            throw new ConflictException(ErrorCode.CONFLICT, "Account already exists");
        }
        passwordPolicy.validate(rawPassword);

        PasswordHasher.Hashed hashed = passwordHasher.hash(rawPassword);
        Account account = new Account(
                UuidV7.generate(),
                isEmail ? target : null,
                isEmail ? null : target,
                hashed.hash(),
                hashed.salt(),
                AccountRole.ROLE_ACCOUNT);
        accounts.save(account);
        patients.createPrimaryFor(account, primaryProfile);
        // Household address captured at signup, shared across the account's
        // patients. Same transaction: no account without its address.
        addresses.upsert(account.getId(), address);
        // Record the account-level consents accepted at signup (Terms, Privacy Policy,
        // Health-data processing) against the current legal-document versions — same
        // transaction, so an account never exists without its consent trail.
        consents.recordRegistrationConsents(account.getId(), device.ipAddress(), device.userAgent());
        return sessions.issue(account, device);
    }
}
