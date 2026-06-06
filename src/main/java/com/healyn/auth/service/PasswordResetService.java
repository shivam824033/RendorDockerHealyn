package com.healyn.auth.service;

import com.healyn.auth.domain.Account;
import com.healyn.auth.domain.OtpChallenge;
import com.healyn.auth.domain.OtpChannel;
import com.healyn.auth.domain.OtpPurpose;
import com.healyn.auth.repository.AccountRepository;
import com.healyn.common.error.ErrorCode;
import com.healyn.common.error.UnprocessableException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PasswordResetService {

    private final AccountRepository accounts;
    private final OtpService otp;
    private final PasswordHasher passwordHasher;
    private final DeviceSessionService sessions;

    public PasswordResetService(AccountRepository accounts, OtpService otp,
                                PasswordHasher passwordHasher, DeviceSessionService sessions) {
        this.accounts = accounts;
        this.otp = otp;
        this.passwordHasher = passwordHasher;
        this.sessions = sessions;
    }

    @Transactional
    public UUID start(String email, String phone) {
        String target = email != null ? email.toLowerCase() : phone;
        OtpChannel channel = email != null ? OtpChannel.EMAIL : OtpChannel.SMS;
        UUID accountId = (email != null
                ? accounts.findByEmail(target)
                : accounts.findByPhoneE164(target))
                .map(Account::getId).orElse(null);
        return otp.issue(target, channel, OtpPurpose.PASSWORD_RESET, accountId);
    }

    @Transactional
    public void complete(UUID challengeId, String code, String newPassword) {
        OtpChallenge challenge = otp.verify(challengeId, code, OtpPurpose.PASSWORD_RESET);
        validatePassword(newPassword);

        Account account = (challenge.getChannel() == OtpChannel.EMAIL
                ? accounts.findByEmail(challenge.getTarget())
                : accounts.findByPhoneE164(challenge.getTarget()))
                .orElseThrow(() -> new UnprocessableException(ErrorCode.UNPROCESSABLE, "Account not found"));

        PasswordHasher.Hashed h = passwordHasher.hash(newPassword);
        account.replacePassword(h.hash(), h.salt());
        account.unlock();
        sessions.revokeAllForAccount(account.getId());
    }

    private static void validatePassword(String pw) {
        if (pw == null || pw.length() < 10 || pw.length() > 128) {
            throw new UnprocessableException(ErrorCode.UNPROCESSABLE, "Password must be 10-128 characters");
        }
        if (pw.indexOf('\0') >= 0) {
            throw new UnprocessableException(ErrorCode.UNPROCESSABLE, "Password contains forbidden character");
        }
    }
}
