package com.healyn.availability.service;

import java.time.Instant;

public record NewBlackoutWindow(Instant startsAt, Instant endsAt, String reason) {}
