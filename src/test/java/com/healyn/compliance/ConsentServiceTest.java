package com.healyn.compliance;

import com.healyn.audit.service.AuditLogger;
import com.healyn.auth.repository.AccountRepository;
import com.healyn.compliance.config.ComplianceProperties;
import com.healyn.compliance.domain.ConsentRecord;
import com.healyn.compliance.domain.ConsentType;
import com.healyn.compliance.domain.LegalDocument;
import com.healyn.compliance.domain.LegalDocumentKind;
import com.healyn.compliance.repository.ConsentRecordRepository;
import com.healyn.compliance.repository.LegalDocumentRepository;
import com.healyn.compliance.service.ConsentService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConsentServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-14T10:00:00Z");

    private final ConsentRecordRepository consents = mock(ConsentRecordRepository.class);
    private final LegalDocumentRepository documents = mock(LegalDocumentRepository.class);
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final AuditLogger audit = mock(AuditLogger.class);
    private final ComplianceProperties props =
            new ComplianceProperties(true, 60_000, 30, "en", false, 2920);
    private final ConsentService service =
            new ConsentService(consents, documents, accounts, audit, props, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void registration_records_three_account_consents_with_version_snapshot() {
        UUID accountId = UUID.randomUUID();
        // Build the document mocks first: stubbing them inline inside the when(...).thenReturn(...)
        // below would nest stubbing calls and trip Mockito's UnfinishedStubbing detector.
        LegalDocument tos = legalDoc("tos-2026-06-14");
        LegalDocument privacy = legalDoc("pp-2026-06-14");
        when(accounts.findById(accountId)).thenReturn(Optional.empty());
        when(documents.findByKindAndLocaleAndCurrentIsTrue(eq(LegalDocumentKind.TERMS_OF_SERVICE), eq("en")))
                .thenReturn(Optional.of(tos));
        when(documents.findByKindAndLocaleAndCurrentIsTrue(eq(LegalDocumentKind.PRIVACY_POLICY), eq("en")))
                .thenReturn(Optional.of(privacy));

        service.recordRegistrationConsents(accountId, "1.2.3.4", "JUnit");

        ArgumentCaptor<ConsentRecord> captor = ArgumentCaptor.forClass(ConsentRecord.class);
        verify(consents, org.mockito.Mockito.times(3)).save(captor.capture());
        List<ConsentRecord> saved = captor.getAllValues();
        assertThat(saved).extracting(ConsentRecord::getConsentType)
                .containsExactly(ConsentType.TERMS_OF_SERVICE, ConsentType.PRIVACY_POLICY,
                        ConsentType.HEALTH_DATA_PROCESSING);
        assertThat(saved).allMatch(ConsentRecord::isGranted);
        // Terms / Privacy snapshot the current document version; health-data processing has none.
        assertThat(saved.get(0).getDocumentVersion()).isEqualTo("tos-2026-06-14");
        assertThat(saved.get(1).getDocumentVersion()).isEqualTo("pp-2026-06-14");
        assertThat(saved.get(2).getDocumentVersion()).isNull();
    }

    @Test
    void withdraw_records_a_non_granted_row() {
        UUID accountId = UUID.randomUUID();
        when(accounts.findById(any())).thenReturn(Optional.empty());

        ConsentRecord record = service.withdraw(accountId, null, ConsentType.HEALTH_DATA_PROCESSING, null, null);

        assertThat(record.isGranted()).isFalse();
        assertThat(record.getWithdrawnAt()).isEqualTo(NOW);
        verify(consents).save(any(ConsentRecord.class));
    }

    private static LegalDocument legalDoc(String version) {
        LegalDocument doc = mock(LegalDocument.class);
        when(doc.getId()).thenReturn(UUID.randomUUID());
        when(doc.getVersion()).thenReturn(version);
        return doc;
    }
}
