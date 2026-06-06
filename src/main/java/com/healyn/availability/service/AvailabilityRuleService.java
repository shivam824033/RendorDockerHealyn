package com.healyn.availability.service;

import com.healyn.auth.domain.AccountRole;
import com.healyn.availability.domain.AvailabilityRule;
import com.healyn.availability.policy.AvailabilityWriteAccess;
import com.healyn.availability.repository.AvailabilityRuleRepository;
import com.healyn.common.error.ErrorCode;
import com.healyn.common.error.NotFoundException;
import com.healyn.common.error.UnprocessableException;
import com.healyn.common.id.UuidV7;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class AvailabilityRuleService {

    private final AvailabilityRuleRepository rules;
    private final AvailabilityWriteAccess writeAccess;

    public AvailabilityRuleService(AvailabilityRuleRepository rules, AvailabilityWriteAccess writeAccess) {
        this.rules = rules;
        this.writeAccess = writeAccess;
    }

    @Transactional(readOnly = true)
    public List<AvailabilityRule> listForPhysio(UUID physiotherapistId, AccountRole role) {
        writeAccess.requirePhysio(role);
        return rules.findByPhysio(physiotherapistId);
    }

    @Transactional
    public AvailabilityRule create(UUID physiotherapistId, AccountRole role, NewAvailabilityRule input) {
        writeAccess.requirePhysio(role);
        ZoneId zone = parseZone(input.timezone());
        validateTimes(input.startTime(), input.endTime(), input.slotMinutes());
        validateEffective(input.effectiveFrom(), input.effectiveTo());
        AvailabilityRule rule = new AvailabilityRule(
                UuidV7.generate(),
                physiotherapistId,
                input.dayOfWeek(),
                input.startTime(),
                input.endTime(),
                input.slotMinutes(),
                zone.getId(),
                input.effectiveFrom(),
                input.effectiveTo());
        return rules.save(rule);
    }

    @Transactional
    public AvailabilityRule update(UUID physiotherapistId, AccountRole role, UUID ruleId, AvailabilityRuleUpdate u) {
        writeAccess.requirePhysio(role);
        AvailabilityRule rule = loadOwn(physiotherapistId, ruleId);
        if (u.timezone() != null) rule.setTimezone(parseZone(u.timezone()).getId());
        if (u.dayOfWeek() != null) rule.setDayOfWeek(u.dayOfWeek());
        if (u.startTime() != null) rule.setStartTime(u.startTime());
        if (u.endTime() != null) rule.setEndTime(u.endTime());
        if (u.slotMinutes() != null) rule.setSlotMinutes(u.slotMinutes());
        if (u.effectiveFrom() != null) rule.setEffectiveFrom(u.effectiveFrom());
        if (u.effectiveTo() != null) rule.setEffectiveTo(u.effectiveTo());
        validateTimes(rule.getStartTime(), rule.getEndTime(), rule.getSlotMinutes());
        validateEffective(rule.getEffectiveFrom(), rule.getEffectiveTo());
        return rule;
    }

    @Transactional
    public void archive(UUID physiotherapistId, AccountRole role, UUID ruleId) {
        writeAccess.requirePhysio(role);
        AvailabilityRule rule = loadOwn(physiotherapistId, ruleId);
        if (rule.getEffectiveTo() != null) return;
        LocalDate today = LocalDate.now(ZoneId.of(rule.getTimezone()));
        rule.setEffectiveTo(today);
    }

    private AvailabilityRule loadOwn(UUID physiotherapistId, UUID ruleId) {
        AvailabilityRule rule = rules.findById(ruleId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.AVAILABILITY_RULE_NOT_FOUND, "Availability rule not found"));
        if (!rule.getPhysiotherapistId().equals(physiotherapistId)) {
            throw new NotFoundException(ErrorCode.AVAILABILITY_RULE_NOT_FOUND, "Availability rule not found");
        }
        return rule;
    }

    private static ZoneId parseZone(String tz) {
        try {
            return ZoneId.of(tz);
        } catch (DateTimeException e) {
            throw new UnprocessableException(ErrorCode.AVAILABILITY_INVALID_TIMEZONE,
                    "Unknown IANA timezone: " + tz);
        }
    }

    private static void validateTimes(LocalTime start, LocalTime end, short slotMinutes) {
        if (!end.isAfter(start)) {
            throw new UnprocessableException(ErrorCode.AVAILABILITY_INVALID_RANGE,
                    "End time must be after start time");
        }
        if (slotMinutes < 5 || slotMinutes > 240) {
            throw new UnprocessableException(ErrorCode.AVAILABILITY_INVALID_RANGE,
                    "slotMinutes must be between 5 and 240");
        }
        if (start.toSecondOfDay() % (slotMinutes * 60) != 0
                || end.toSecondOfDay() % (slotMinutes * 60) != 0) {
            throw new UnprocessableException(ErrorCode.AVAILABILITY_INVALID_RANGE,
                    "Start and end must align on slotMinutes boundaries from 00:00");
        }
    }

    private static void validateEffective(LocalDate from, LocalDate to) {
        if (from == null) {
            throw new UnprocessableException(ErrorCode.AVAILABILITY_INVALID_RANGE,
                    "effectiveFrom is required");
        }
        if (to != null && to.isBefore(from)) {
            throw new UnprocessableException(ErrorCode.AVAILABILITY_INVALID_RANGE,
                    "effectiveTo must be on or after effectiveFrom");
        }
    }
}
