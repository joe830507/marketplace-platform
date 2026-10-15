package com.marketplace.shared.internal.support;

import com.marketplace.shared.IdGenerator;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * UUIDv7 (RFC 9562): 48 bits of Unix milliseconds, then version, a 12-bit counter, variant and 62
 * random bits.
 *
 * <p>Within one process the keys strictly increase, also within the same millisecond (RFC 9562 6.2,
 * method 1): the counter starts at a random value in each new millisecond and counts up. Lists
 * ordered newest first therefore keep the creation order of rows made in the same instant, and
 * inserts stay near the end of the index (ADR_010).
 */
@Component
public class UuidV7Generator implements IdGenerator {

  private static final int COUNTER_MAX = 0xFFF;

  private final Clock clock;
  private final SecureRandom random = new SecureRandom();
  private long lastMillis = -1;
  private int counter;

  UuidV7Generator(Clock clock) {
    this.clock = clock;
  }

  @Override
  public synchronized UUID newId() {
    long millis = clock.millis();
    if (millis > lastMillis) {
      lastMillis = millis;
      // Start in the lower half so that many keys fit in the same millisecond.
      counter = random.nextInt(COUNTER_MAX / 2);
    } else if (++counter > COUNTER_MAX) {
      // Counter exhausted, or the clock moved back: borrow the next millisecond to keep the order.
      lastMillis++;
      counter = 0;
    }
    long mostSignificant = (lastMillis << 16) | 0x7000L | counter;
    long leastSignificant = (random.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL) | 0x8000_0000_0000_0000L;
    return new UUID(mostSignificant, leastSignificant);
  }
}
