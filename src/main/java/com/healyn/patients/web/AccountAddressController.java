package com.healyn.patients.web;

import com.healyn.patients.domain.AccountAddress;
import com.healyn.patients.service.AccountAddressService;
import com.healyn.patients.service.AddressData;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/// The signed-in account's own household postal address (the one shared across
/// its patients). Scoped entirely to the caller's account — there is no path
/// parameter and no cross-account access, so no PatientAccessPolicy check is
/// needed. See docs/API_STANDARDS.md §9.2.
@RestController
@RequestMapping("/account/address")
public class AccountAddressController {

    private final AccountAddressService addresses;

    public AccountAddressController(AccountAddressService addresses) {
        this.addresses = addresses;
    }

    @GetMapping
    public PatientDtos.AccountAddressResponse get(@AuthenticationPrincipal Jwt jwt) {
        UUID accountId = UUID.fromString(jwt.getSubject());
        return new PatientDtos.AccountAddressResponse(
                addresses.findForAccount(accountId).map(PatientMapper::toAddressView).orElse(null));
    }

    @PutMapping
    public PatientDtos.AddressView put(@AuthenticationPrincipal Jwt jwt,
                                       @Valid @RequestBody PatientDtos.AddressRequest body) {
        UUID accountId = UUID.fromString(jwt.getSubject());
        AccountAddress saved = addresses.upsert(accountId, new AddressData(
                body.line1(), body.line2(), body.city(),
                body.state(), body.postalCode(), body.country()));
        return PatientMapper.toAddressView(saved);
    }
}
