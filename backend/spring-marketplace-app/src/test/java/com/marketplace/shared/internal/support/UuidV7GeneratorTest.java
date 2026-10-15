package com.marketplace.shared.internal.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketplace.testsupport.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UuidV7GeneratorTest {

  private final MutableClock clock =
      new MutableClock(Instant.parse("2026-10-15T06:30:00.123Z"), ZoneOffset.UTC);
  private final UuidV7Generator generator = new UuidV7Generator(clock);

  @Test
  @DisplayName("產生 RFC 9562 的 UUIDv7，前 48 位元是時鐘的毫秒數")
  void layout() {
    UUID id = generator.newId();

    assertThat(id.version()).isEqualTo(7);
    assertThat(id.variant()).isEqualTo(2);
    assertThat(id.getMostSignificantBits() >>> 16).isEqualTo(clock.millis());
  }

  @Test
  @DisplayName("較晚產生的識別碼排序在後")
  void laterIdsSortAfterEarlierOnes() {
    UUID earlier = generator.newId();
    clock.advance(Duration.ofMillis(1));
    UUID later = generator.newId();

    assertThat(later.toString()).isGreaterThan(earlier.toString());
  }

  @Test
  @DisplayName("同一毫秒內產生的識別碼不重複")
  void idsInTheSameMillisecondDiffer() {
    assertThat(generator.newId()).isNotEqualTo(generator.newId());
  }
}
