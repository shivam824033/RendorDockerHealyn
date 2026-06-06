package com.healyn.auth.config;

import com.healyn.auth.domain.Account;
import com.healyn.auth.domain.AccountRole;
import com.healyn.auth.repository.AccountRepository;
import com.healyn.auth.service.PasswordHasher;
import com.healyn.common.id.UuidV7;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds a single {@code ROLE_PHYSIO} login in the {@code local}/{@code dev}
 * profiles so the physiotherapist app is reachable without a manual step. There
 * is no physio self-registration by design (PROJECT_CONTEXT §5.2). Idempotent
 * and never active in prod, where the physiotherapist is provisioned by an
 * operator (README §5.1). The password is read from config but never logged
 * (CLAUDE.md §3).
 */
@Component
@Profile({"local", "dev"})
public class DevPhysioSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevPhysioSeeder.class);

    private final AccountRepository accounts;
    private final PasswordHasher passwordHasher;
    private final String email;
    private final String password;

    public DevPhysioSeeder(
            AccountRepository accounts,
            PasswordHasher passwordHasher,
            @Value("${healyn.dev.physio.email:physio@healyn.local}") String email,
            @Value("${healyn.dev.physio.password:Physio!Dev123}") String password) {
        this.accounts = accounts;
        this.passwordHasher = passwordHasher;
        this.email = email == null ? null : email.toLowerCase();
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            log.warn("Dev physio seed skipped: healyn.dev.physio.email/password not configured");
            return;
        }
        if (accounts.existsByEmail(email)) {
            log.info("Dev physiotherapist account already present ({}); skipping seed", email);
            return;
        }
        PasswordHasher.Hashed hashed = passwordHasher.hash(password);
        Account physio = new Account(
                UuidV7.generate(), email, null, hashed.hash(), hashed.salt(), AccountRole.ROLE_PHYSIO);
        accounts.save(physio);
        log.info("Seeded dev physiotherapist account: {}", email);
    }
}
