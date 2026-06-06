package com.healyn.availability.service;

import java.time.Instant;
import java.util.UUID;

public record Slot(UUID physiotherapistId, Instant startsAt, Instant endsAt, int durationMinutes) {}
