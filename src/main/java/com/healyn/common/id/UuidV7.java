package com.healyn.common.id;

import java.security.SecureRandom;
import java.util.UUID;

public final class UuidV7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidV7() {}

    public static UUID generate() {
        long timestampMs = System.currentTimeMillis();
        byte[] randomBytes = new byte[10];
        RANDOM.nextBytes(randomBytes);

        long msb = (timestampMs & 0xFFFFFFFFFFFFL) << 16;
        msb |= 0x7000L;
        msb |= ((randomBytes[0] & 0x0FL) << 8) | (randomBytes[1] & 0xFFL);

        long lsb = 0;
        lsb |= 0x8000000000000000L;
        lsb |= ((long) (randomBytes[2] & 0x3F)) << 56;
        lsb |= ((long) (randomBytes[3] & 0xFF)) << 48;
        lsb |= ((long) (randomBytes[4] & 0xFF)) << 40;
        lsb |= ((long) (randomBytes[5] & 0xFF)) << 32;
        lsb |= ((long) (randomBytes[6] & 0xFF)) << 24;
        lsb |= ((long) (randomBytes[7] & 0xFF)) << 16;
        lsb |= ((long) (randomBytes[8] & 0xFF)) << 8;
        lsb |= ((long) (randomBytes[9] & 0xFF));

        return new UUID(msb, lsb);
    }
}
