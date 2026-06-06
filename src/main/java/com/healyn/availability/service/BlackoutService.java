package com.healyn.availability.service;

import com.healyn.auth.domain.AccountRole;
import com.healyn.availability.domain.BlackoutWindow;
import com.healyn.availability.policy.AvailabilityWriteAccess;
import com.healyn.availability.repository.BlackoutWindowRepository;
import com.healyn.common.error.ConflictException;
import com.healyn.common.error.ErrorCode;
import com.healyn.common.error.NotFoundException;
import com.healyn.common.error.UnprocessableException;
import com.healyn.common.id.UuidV7;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class BlackoutService {

    private static final String PG_EXCLUSION_VIOLATION = "23P01";

    private final BlackoutWindowRepository blackouts;
    private final AvailabilityWriteAccess writeAccess;

    public BlackoutService(BlackoutWindowRepository blackouts, AvailabilityWriteAccess writeAccess) {
        this.blackouts = blackouts;
        this.writeAccess = writeAccess;
    }

    @Transactional(readOnly = true)
    public List<BlackoutWindow> listForPhysio(UUID physiotherapistId, AccountRole role) {
        writeAccess.requirePhysio(role);
        return blackouts.findByPhysio(physiotherapistId);
    }

    @Transactional
    public BlackoutWindow create(UUID physiotherapistId, AccountRole role, NewBlackoutWindow input) {
        writeAccess.requirePhysio(role);
        validate(input.startsAt(), input.endsAt());
        BlackoutWindow blackout = new BlackoutWindow(
                UuidV7.generate(),
                physiotherapistId,
                input.startsAt(),
                input.endsAt(),
                input.reason());
        try {
            return blackouts.saveAndFlush(blackout);
        } catch (DataIntegrityViolationException e) {
            if (isExclusionViolation(e)) {
                throw new ConflictException(ErrorCode.AVAILABILITY_BLACKOUT_OVERLAP,
                        "Blackout overlaps an existing window for this physiotherapist");
            }
            throw e;
        }
    }

    @Transactional
    public void delete(UUID physiotherapistId, AccountRole role, UUID blackoutId) {
        writeAccess.requirePhysio(role);
        BlackoutWindow blackout = blackouts.findById(blackoutId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.AVAILABILITY_BLACKOUT_NOT_FOUND, "Blackout not found"));
        if (!blackout.getPhysiotherapistId().equals(physiotherapistId)) {
            throw new NotFoundException(ErrorCode.AVAILABILITY_BLACKOUT_NOT_FOUND, "Blackout not found");
        }
        blackouts.delete(blackout);
    }

    private static void validate(Instant startsAt, Instant endsAt) {
        if (startsAt == null || endsAt == null) {
            throw new UnprocessableException(ErrorCode.AVAILABILITY_INVALID_RANGE,
                    "startsAt and endsAt are required");
        }
        if (!endsAt.isAfter(startsAt)) {
            throw new UnprocessableException(ErrorCode.AVAILABILITY_INVALID_RANGE,
                    "endsAt must be after startsAt");
        }
    }

    private static boolean isExclusionViolation(DataIntegrityViolationException e) {
        Throwable cause = e.getCause();
        while (cause != null) {
            if (cause instanceof PSQLException psql && PG_EXCLUSION_VIOLATION.equals(psql.getSQLState())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
