package com.healyn.patients.service;

/// Plain carrier for a household postal address as it crosses the service
/// boundary (registration and the account-address endpoints map their validated
/// request records onto this). [line2] is optional; [country] defaults to
/// "India" when blank.
public record AddressData(
        String line1,
        String line2,
        String city,
        String state,
        String postalCode,
        String country) {
}
