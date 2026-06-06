package com.healyn.availability.web;

import com.healyn.auth.domain.Account;
import com.healyn.auth.domain.AccountRole;
import com.healyn.auth.repository.AccountRepository;
import com.healyn.availability.service.AvailabilityRuleService;
import com.healyn.availability.service.AvailabilityRuleUpdate;
import com.healyn.availability.service.BlackoutService;
import com.healyn.availability.service.NewAvailabilityRule;
import com.healyn.availability.service.NewBlackoutWindow;
import com.healyn.availability.service.Slot;
import com.healyn.availability.service.SlotExpansionService;
import com.healyn.common.error.ErrorCode;
import com.healyn.common.error.NotFoundException;
import com.healyn.common.error.UnprocessableException;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/availability")
public class AvailabilityController {

    private static final int MAX_SLOT_RANGE_DAYS = 31;

    private final AvailabilityRuleService ruleService;
    private final BlackoutService blackoutService;
    private final SlotExpansionService slotExpansion;
    private final AccountRepository accounts;

    public AvailabilityController(AvailabilityRuleService ruleService,
                                  BlackoutService blackoutService,
                                  SlotExpansionService slotExpansion,
                                  AccountRepository accounts) {
        this.ruleService = ruleService;
        this.blackoutService = blackoutService;
        this.slotExpansion = slotExpansion;
        this.accounts = accounts;
    }

    @GetMapping
    public AvailabilityDtos.SlotListResponse listSlots(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(value = "physiotherapist_id", required = false) UUID physiotherapistIdParam,
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        requireValidRange(from, to);
        UUID physiotherapistId = resolvePhysioId(physiotherapistIdParam);
        List<Slot> slots = slotExpansion.expandSlots(physiotherapistId, from, to, List.of());
        List<AvailabilityDtos.SlotView> views = slots.stream().map(AvailabilityMapper::toView).toList();
        return new AvailabilityDtos.SlotListResponse(physiotherapistId, views);
    }

    @GetMapping("/rules")
    public AvailabilityDtos.RuleListResponse listRules(@AuthenticationPrincipal Jwt jwt) {
        UUID physiotherapistId = UUID.fromString(jwt.getSubject());
        AccountRole role = roleOf(jwt);
        List<AvailabilityDtos.RuleView> views = ruleService.listForPhysio(physiotherapistId, role).stream()
                .map(AvailabilityMapper::toView)
                .toList();
        return new AvailabilityDtos.RuleListResponse(views);
    }

    @PostMapping("/rules")
    @ResponseStatus(HttpStatus.CREATED)
    public AvailabilityDtos.RuleView createRule(@AuthenticationPrincipal Jwt jwt,
                                                @Valid @RequestBody AvailabilityDtos.CreateRuleRequest body) {
        UUID physiotherapistId = UUID.fromString(jwt.getSubject());
        AccountRole role = roleOf(jwt);
        NewAvailabilityRule input = new NewAvailabilityRule(
                body.dayOfWeek(), body.startTime(), body.endTime(), body.slotMinutes(),
                body.timezone(), body.effectiveFrom(), body.effectiveTo());
        return AvailabilityMapper.toView(ruleService.create(physiotherapistId, role, input));
    }

    @PatchMapping("/rules/{id}")
    public AvailabilityDtos.RuleView updateRule(@AuthenticationPrincipal Jwt jwt,
                                                @PathVariable("id") UUID id,
                                                @Valid @RequestBody AvailabilityDtos.UpdateRuleRequest body) {
        UUID physiotherapistId = UUID.fromString(jwt.getSubject());
        AccountRole role = roleOf(jwt);
        AvailabilityRuleUpdate update = new AvailabilityRuleUpdate(
                body.dayOfWeek(), body.startTime(), body.endTime(), body.slotMinutes(),
                body.timezone(), body.effectiveFrom(), body.effectiveTo());
        return AvailabilityMapper.toView(ruleService.update(physiotherapistId, role, id, update));
    }

    @DeleteMapping("/rules/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archiveRule(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id) {
        UUID physiotherapistId = UUID.fromString(jwt.getSubject());
        AccountRole role = roleOf(jwt);
        ruleService.archive(physiotherapistId, role, id);
    }

    @GetMapping("/blackouts")
    public AvailabilityDtos.BlackoutListResponse listBlackouts(@AuthenticationPrincipal Jwt jwt) {
        UUID physiotherapistId = UUID.fromString(jwt.getSubject());
        AccountRole role = roleOf(jwt);
        List<AvailabilityDtos.BlackoutView> views = blackoutService.listForPhysio(physiotherapistId, role).stream()
                .map(AvailabilityMapper::toView)
                .toList();
        return new AvailabilityDtos.BlackoutListResponse(views);
    }

    @PostMapping("/blackouts")
    @ResponseStatus(HttpStatus.CREATED)
    public AvailabilityDtos.BlackoutView createBlackout(@AuthenticationPrincipal Jwt jwt,
                                                       @Valid @RequestBody AvailabilityDtos.CreateBlackoutRequest body) {
        UUID physiotherapistId = UUID.fromString(jwt.getSubject());
        AccountRole role = roleOf(jwt);
        NewBlackoutWindow input = new NewBlackoutWindow(body.startsAt(), body.endsAt(), body.reason());
        return AvailabilityMapper.toView(blackoutService.create(physiotherapistId, role, input));
    }

    @DeleteMapping("/blackouts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBlackout(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") UUID id) {
        UUID physiotherapistId = UUID.fromString(jwt.getSubject());
        AccountRole role = roleOf(jwt);
        blackoutService.delete(physiotherapistId, role, id);
    }

    private UUID resolvePhysioId(UUID explicit) {
        if (explicit != null) return explicit;
        return accounts.findFirstByRoleAndDeletedAtIsNullOrderByCreatedAtAsc(AccountRole.ROLE_PHYSIO)
                .map(Account::getId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "No physiotherapist account found"));
    }

    private static void requireValidRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new UnprocessableException(ErrorCode.AVAILABILITY_INVALID_RANGE,
                    "from and to are required and to must be on or after from");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_SLOT_RANGE_DAYS) {
            throw new UnprocessableException(ErrorCode.AVAILABILITY_INVALID_RANGE,
                    "Range cannot exceed " + MAX_SLOT_RANGE_DAYS + " days");
        }
    }

    private static AccountRole roleOf(Jwt jwt) {
        String role = jwt.getClaimAsString("role");
        return role == null ? AccountRole.ROLE_ACCOUNT : AccountRole.valueOf(role);
    }
}
