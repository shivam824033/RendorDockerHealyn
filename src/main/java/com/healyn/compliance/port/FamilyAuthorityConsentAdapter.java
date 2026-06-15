package com.healyn.compliance.port;

import com.healyn.compliance.service.ConsentService;
import com.healyn.patients.port.ConsentRecorderPort;
import org.springframework.stereotype.Component;

import java.util.UUID;

/// Implements the {@code patients} module's {@link ConsentRecorderPort} seam by recording the
/// Family-Member Authority consent via {@link ConsentService}. Keeps {@code patients} free of a
/// dependency on {@code compliance} (the dependency points one way: compliance → patients).
@Component
public class FamilyAuthorityConsentAdapter implements ConsentRecorderPort {

    private final ConsentService consents;

    public FamilyAuthorityConsentAdapter(ConsentService consents) {
        this.consents = consents;
    }

    @Override
    public void recordFamilyAuthority(UUID accountId, UUID patientId, String ipAddress, String userAgent) {
        consents.recordFamilyAuthority(accountId, patientId, ipAddress, userAgent);
    }
}
