package com.healyn.availability.service;

import java.time.Instant;

public record TimeRange(Instant startsAt, Instant endsAt) {

    public TimeRange {
        if (!endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("endsAt must be after startsAt");
        }
    }

    public boolean overlaps(Instant otherStart, Instant otherEnd) {
        return startsAt.isBefore(otherEnd) && endsAt.isAfter(otherStart);
    }
}
