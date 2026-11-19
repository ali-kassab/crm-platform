package com.crmplatform.shared.id;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UuidV7Test {

    @Test
    void hasVersion7AndIetfVariant() {
        UUID id = UuidV7.generate();
        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    void encodesTimestampInMostSignificantBits() {
        long millis = 1_763_000_000_000L;
        UUID id = UuidV7.generate(millis);
        assertThat(id.getMostSignificantBits() >>> 16).isEqualTo(millis);
    }

    @Test
    void laterTimestampsSortAfterEarlierOnes() {
        UUID earlier = UuidV7.generate(1_000L);
        UUID later = UuidV7.generate(2_000L);
        assertThat(later.toString()).isGreaterThan(earlier.toString());
    }

    @Test
    void isUniqueAcrossManyCalls() {
        Set<UUID> ids = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            ids.add(UuidV7.generate());
        }
        assertThat(ids).hasSize(10_000);
    }
}
