package com.healyn.auth.service;

import com.healyn.auth.adapter.OtpSender;
import com.healyn.auth.domain.OtpChallenge;
import com.healyn.auth.domain.OtpChannel;
import com.healyn.auth.domain.OtpPurpose;
import com.healyn.auth.repository.OtpChallengeRepository;
import com.healyn.common.error.ErrorCode;
import com.healyn.common.error.RateLimitedException;
import com.healyn.common.error.UnprocessableException;
import com.healyn.common.id.UuidV7;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class OtpService {

    private static final Duration TTL = Duration.ofMinutes(5);
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofHours(1);
    private static final int RATE_LIMIT_MAX = 3;
    private static final int MAX_ATTEMPTS = 5;

    private final OtpChallengeRepository repo;
    private final OtpSender sender;
    private final SecureRandom random = new SecureRandom();

    public OtpService(OtpChallengeRepository repo, OtpSender sender) {
        this.repo = repo;
        this.sender = sender;
    }

    @Transactional
    public UUID issue(String target, OtpChannel channel, OtpPurpose purpose, UUID accountId) {
        Instant now = Instant.now();
        long recent = repo.countIssuedSince(target, now.minus(RATE_LIMIT_WINDOW));
        if (recent >= RATE_LIMIT_MAX) {
            throw new RateLimitedException(ErrorCode.RATE_LIMITED, "OTP rate limit exceeded for target");
        }
        String code = generateCode();
        OtpChallenge challenge = new OtpChallenge(
                UuidV7.generate(), accountId, target, channel, purpose, sha256(code), now.plus(TTL));
        repo.save(challenge);
        sender.send(target, channel, code);
        return challenge.getId();
    }

    @Transactional
    public OtpChallenge verify(UUID challengeId, String code, OtpPurpose expectedPurpose) {
        OtpChallenge c = repo.findById(challengeId)
                .orElseThrow(() -> invalid("OTP challenge not found"));
        Instant now = Instant.now();

        if (c.getPurpose() != expectedPurpose) throw invalid("OTP purpose mismatch");
        if (c.isConsumed()) throw invalid("OTP already used");
        if (c.isExpired(now)) throw invalid("OTP expired");
        if (c.isExhausted()) throw invalid("OTP attempts exhausted");

        c.recordAttempt();
        if (!MessageDigest.isEqual(c.getCodeHash(), sha256(code))) {
            throw invalid("OTP does not match");
        }
        c.consume();
        return c;
    }

    private String generateCode() {
        int n = random.nextInt(1_000_000);
        return String.format("%06d", n);
    }

    private static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static UnprocessableException invalid(String msg) {
        return new UnprocessableException(ErrorCode.UNPROCESSABLE, msg);
    }
}
