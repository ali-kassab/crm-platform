package com.crmplatform.shared.id;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;

/**
 * Generates RFC 9562 version 7 UUIDs: 48-bit Unix epoch milliseconds followed by random bits.
 * Time-ordered ids keep B-tree indexes compact (ADR-0002).
 */
public final class UuidV7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidV7() {
    }

    public static UUID generate() {
        return generate(Clock.systemUTC().millis());
    }

    static UUID generate(long epochMillis) {
        byte[] randomBytes = new byte[10];
        RANDOM.nextBytes(randomBytes);

        long msb = (epochMillis & 0xFFFF_FFFF_FFFFL) << 16;
        msb |= 0x7000L; // version 7
        msb |= ((randomBytes[0] & 0x0FL) << 8) | (randomBytes[1] & 0xFFL);

        long lsb = 0x8000_0000_0000_0000L; // IETF variant (10xx)
        lsb |= (randomBytes[2] & 0x3FL) << 56;
        for (int i = 3; i < 10; i++) {
            lsb |= (randomBytes[i] & 0xFFL) << (8 * (9 - i));
        }
        return new UUID(msb, lsb);
    }
}
