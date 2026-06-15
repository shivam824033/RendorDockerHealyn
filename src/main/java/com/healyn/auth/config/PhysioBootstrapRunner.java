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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-off, operator-driven provisioning of the single {@code ROLE_PHYSIO} login in
 * environments where {@link DevPhysioSeeder} does not run (i.e. prod). There is no physio
 * self-registration by design (PROJECT_CONTEXT §5.2), and the password cannot be hashed
 * offline because {@link PasswordHasher} mixes a server-side pepper into every hash — so the
 * account must be created in-process with the live hasher. This runner is therefore the
 * supported provisioning path (README §5.2).
 *
 * <p>Gated behind {@code healyn.bootstrap.physio.enabled} (default {@code false}). The operator
 * sets the flag plus a temporary email/password secret, boots once, then turns the flag off and
 * removes the password secret; the physio rotates the temporary password via the reset flow on
 * first login. Idempotent: skips if the email already exists. Never logs the password
 * (CLAUDE.md §3).
 */
@Component
public class PhysioBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PhysioBootstrapRunner.class);

    private final AccountRepository accounts;
    private final PasswordHasher passwordHasher;
    private final boolean enabled;
    private final String email;
    private final String password;

    public PhysioBootstrapRunner(
            AccountRepository accounts,
            PasswordHasher passwordHasher,
            @Value("${healyn.bootstrap.physio.enabled:false}") boolean enabled,
            @Value("${healyn.bootstrap.physio.email:}") String email,
            @Value("${healyn.bootstrap.physio.password:}") String password) {
        this.accounts = accounts;
        this.passwordHasher = passwordHasher;
        this.enabled = enabled;
        this.email = email == null ? null : email.toLowerCase();
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            log.warn("Physio bootstrap enabled but email/password not configured; skipping");
            return;
        }
        if (accounts.existsByEmail(email)) {
            log.info("Physiotherapist account already present ({}); bootstrap is a no-op", email);
            return;
        }
        PasswordHasher.Hashed hashed = passwordHasher.hash(password);
        Account physio = new Account(
                UuidV7.generate(), email, null, hashed.hash(), hashed.salt(), AccountRole.ROLE_PHYSIO);
        accounts.save(physio);
        log.info("Bootstrapped physiotherapist account: {}. "
                + "Rotate the temporary password and disable healyn.bootstrap.physio.enabled.", email);
    }
}
