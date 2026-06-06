package com.healyn.auth.service;

import com.healyn.auth.config.AuthProperties;
import com.healyn.auth.domain.Account;
import com.healyn.auth.domain.DeviceSession;
import com.healyn.auth.repository.AccountRepository;
import com.healyn.auth.repository.DeviceSessionRepository;
import com.healyn.common.error.ErrorCode;
import com.healyn.common.error.ForbiddenException;
import com.healyn.common.error.NotFoundException;
import com.healyn.common.error.UnauthorizedException;
import com.healyn.common.id.UuidV7;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class DeviceSessionService {

    private static final Logger log = LoggerFactory.getLogger(DeviceSessionService.class);

    private final DeviceSessionRepository sessions;
    private final AccountRepository accounts;
    private final AccessTokenIssuer accessTokens;
    private final AuthProperties.Jwt jwtProps;

    public DeviceSessionService(DeviceSessionRepository sessions,
                                AccountRepository accounts,
                                AccessTokenIssuer accessTokens,
                                AuthProperties.Jwt jwtProps) {
        this.sessions = sessions;
        this.accounts = accounts;
        this.accessTokens = accessTokens;
        this.jwtProps = jwtProps;
    }

    @Transactional
    public IssuedSession issue(Account account, DeviceMeta device) {
        String refresh = RefreshTokens.generate();
        Instant refreshExp = Instant.now().plus(Duration.ofDays(jwtProps.refreshTokenTtlDays()));
        DeviceSession session = new DeviceSession(
                UuidV7.generate(), account.getId(), RefreshTokens.hash(refresh),
                device.deviceId(), device.deviceLabel(), device.fcmToken(),
                device.ipAddress(), device.userAgent(), refreshExp);
        sessions.save(session);
        AccessTokenIssuer.Issued access = accessTokens.issue(account);
        return new IssuedSession(session.getId(), access.token(), access.expiresAt(), refresh, refreshExp);
    }

    @Transactional
    public IssuedSession rotate(String presentedRefreshToken, DeviceMeta device) {
        byte[] hash = RefreshTokens.hash(presentedRefreshToken);
        DeviceSession existing = sessions.findByRefreshTokenHash(hash).orElseThrow(this::invalidRefresh);
        Instant now = Instant.now();

        if (existing.getRevokedAt() != null) {
            log.warn("Refresh-token reuse detected for account {} — revoking all sessions", existing.getAccountId());
            sessions.revokeAllForAccount(existing.getAccountId(), now);
            throw invalidRefresh();
        }
        if (!existing.isActive(now)) {
            existing.revoke();
            throw invalidRefresh();
        }

        Account account = accounts.findById(existing.getAccountId()).orElseThrow(this::invalidRefresh);

        existing.revoke();
        String newRefresh = RefreshTokens.generate();
        Instant newRefreshExp = now.plus(Duration.ofDays(jwtProps.refreshTokenTtlDays()));
        DeviceSession next = new DeviceSession(
                UuidV7.generate(), account.getId(), RefreshTokens.hash(newRefresh),
                existing.getDeviceId(), existing.getDeviceLabel(),
                device.fcmToken() != null ? device.fcmToken() : existing.getFcmToken(),
                device.ipAddress() != null ? device.ipAddress() : existing.getIpAddress(),
                device.userAgent() != null ? device.userAgent() : existing.getUserAgent(),
                newRefreshExp);
        sessions.save(next);

        AccessTokenIssuer.Issued access = accessTokens.issue(account);
        return new IssuedSession(next.getId(), access.token(), access.expiresAt(), newRefresh, newRefreshExp);
    }

    @Transactional(readOnly = true)
    public List<DeviceSession> listActive(UUID accountId) {
        return sessions.findAllByAccountIdAndRevokedAtIsNull(accountId);
    }

    @Transactional
    public void revoke(UUID sessionId, UUID requestingAccountId) {
        DeviceSession s = sessions.findById(sessionId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "Session not found"));
        if (!s.getAccountId().equals(requestingAccountId)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, "Not your session");
        }
        s.revoke();
    }

    @Transactional
    public int revokeAllForAccount(UUID accountId) {
        return sessions.revokeAllForAccount(accountId, Instant.now());
    }

    private UnauthorizedException invalidRefresh() {
        return new UnauthorizedException(ErrorCode.UNAUTHORIZED, "Invalid or expired refresh token");
    }
}
