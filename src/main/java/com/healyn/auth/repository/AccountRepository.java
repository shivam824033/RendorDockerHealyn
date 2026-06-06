package com.healyn.auth.repository;

import com.healyn.auth.domain.Account;
import com.healyn.auth.domain.AccountRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByEmail(String email);

    Optional<Account> findByPhoneE164(String phoneE164);

    boolean existsByEmail(String email);

    boolean existsByPhoneE164(String phoneE164);

    Optional<Account> findFirstByRoleAndDeletedAtIsNullOrderByCreatedAtAsc(AccountRole role);
}
