package com.healyn.compliance.port;

import com.healyn.auth.port.RegistrationConsentRecorder;
import com.healyn.compliance.service.ConsentService;
import org.springframework.stereotype.Component;

import java.util.UUID;

/// Implements the {@code auth} module's {@link RegistrationConsentRecorder} seam by recording
/// the account-level signup consents via {@link ConsentService}. Keeps {@code auth} free of a
/// dependency on {@code compliance} (the dependency points one way: compliance → auth).
@Component
public class RegistrationConsentAdapter implements RegistrationConsentRecorder {

    private final ConsentService consents;

    public RegistrationConsentAdapter(ConsentService consents) {
        this.consents = consents;
    }

    @Override
    public void recordRegistrationConsents(UUID accountId, String ipAddress, String userAgent) {
        consents.recordRegistrationConsents(accountId, ipAddress, userAgent);
    }
}
