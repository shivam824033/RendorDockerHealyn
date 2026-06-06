package com.healyn.auth.service;

import com.healyn.auth.adapter.OtpSender;
import com.healyn.auth.domain.OtpChallenge;
import com.healyn.auth.domain.OtpChannel;
import com.healyn.auth.domain.OtpPurpose;
import com.healyn.auth.repository.OtpChallengeRepository;
import com.healyn.common.error.RateLimitedException;
import com.healyn.common.error.UnprocessableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OtpServiceTest {

    private OtpChallengeRepository repo;
    private OtpSender sender;
    private OtpService service;
    private AtomicReference<String> sentCode;

    @BeforeEach
    void setUp() {
        repo = mock(OtpChallengeRepository.class);
        sentCode = new AtomicReference<>();
        sender = (target, channel, code) -> sentCode.set(code);
        service = new OtpService(repo, sender);
        when(repo.save(any(OtpChallenge.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void issue_below_rate_limit_succeeds_and_sends_code() {
        when(repo.countIssuedSince(anyString(), any())).thenReturn(2L);

        UUID id = service.issue("user@example.com", OtpChannel.EMAIL, OtpPurpose.REGISTRATION, null);

        assertThat(id).isNotNull();
        assertThat(sentCode.get()).matches("\\d{6}");

        ArgumentCaptor<OtpChallenge> saved = ArgumentCaptor.forClass(OtpChallenge.class);
        org.mockito.Mockito.verify(repo).save(saved.capture());
        assertThat(saved.getValue().getTarget()).isEqualTo("user@example.com");
    }

    @Test
    void issue_at_rate_limit_throws_429() {
        when(repo.countIssuedSince(anyString(), any())).thenReturn(3L);

        assertThatThrownBy(() -> service.issue("user@example.com", OtpChannel.EMAIL, OtpPurpose.REGISTRATION, null))
                .isInstanceOf(RateLimitedException.class);
    }

    @Test
    void verify_with_correct_code_consumes_challenge() {
        when(repo.countIssuedSince(anyString(), any())).thenReturn(0L);
        UUID id = service.issue("u@x.com", OtpChannel.EMAIL, OtpPurpose.REGISTRATION, null);
        String code = sentCode.get();

        OtpChallenge issued = capturedChallenge();
        when(repo.findById(id)).thenReturn(Optional.of(issued));

        OtpChallenge verified = service.verify(id, code, OtpPurpose.REGISTRATION);

        assertThat(verified.isConsumed()).isTrue();
    }

    @Test
    void verify_with_wrong_code_records_attempt_and_throws() {
        when(repo.countIssuedSince(anyString(), any())).thenReturn(0L);
        UUID id = service.issue("u@x.com", OtpChannel.EMAIL, OtpPurpose.REGISTRATION, null);

        OtpChallenge issued = capturedChallenge();
        when(repo.findById(id)).thenReturn(Optional.of(issued));

        assertThatThrownBy(() -> service.verify(id, "000000", OtpPurpose.REGISTRATION))
                .isInstanceOf(UnprocessableException.class);
        assertThat(issued.getAttempts()).isEqualTo(1);
        assertThat(issued.isConsumed()).isFalse();
    }

    @Test
    void verify_after_max_attempts_throws_exhausted() {
        when(repo.countIssuedSince(anyString(), any())).thenReturn(0L);
        UUID id = service.issue("u@x.com", OtpChannel.EMAIL, OtpPurpose.REGISTRATION, null);

        OtpChallenge issued = capturedChallenge();
        when(repo.findById(id)).thenReturn(Optional.of(issued));

        for (int i = 0; i < 5; i++) {
            try { service.verify(id, "000000", OtpPurpose.REGISTRATION); } catch (RuntimeException ignored) {}
        }
        assertThatThrownBy(() -> service.verify(id, "000000", OtpPurpose.REGISTRATION))
                .isInstanceOf(UnprocessableException.class)
                .hasMessageContaining("exhausted");
    }

    @Test
    void verify_with_wrong_purpose_throws() {
        when(repo.countIssuedSince(anyString(), any())).thenReturn(0L);
        UUID id = service.issue("u@x.com", OtpChannel.EMAIL, OtpPurpose.REGISTRATION, null);

        OtpChallenge issued = capturedChallenge();
        when(repo.findById(id)).thenReturn(Optional.of(issued));

        assertThatThrownBy(() -> service.verify(id, sentCode.get(), OtpPurpose.PASSWORD_RESET))
                .isInstanceOf(UnprocessableException.class)
                .hasMessageContaining("purpose");
    }

    private OtpChallenge capturedChallenge() {
        ArgumentCaptor<OtpChallenge> cap = ArgumentCaptor.forClass(OtpChallenge.class);
        org.mockito.Mockito.verify(repo, org.mockito.Mockito.atLeastOnce()).save(cap.capture());
        return cap.getValue();
    }
}
