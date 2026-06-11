package com.healyn.appointments.service;

import com.healyn.appointments.domain.Appointment;

/// One global-search hit: a matched appointment paired with its patient's display fields
/// (name + human-friendly number) resolved in one bounded lookup. The patient fields are
/// PHI but only ever returned to a caller already authorised for that patient (the search
/// is scoped to the actor's patients), mirroring what the patient detail view exposes.
public record AppointmentSearchResult(Appointment appointment, String patientName, String patientNumber) {}
