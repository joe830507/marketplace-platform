package com.marketplace.shared.internal.support;

import com.marketplace.shared.IdGenerator;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * UUIDv7 (RFC 9562): 48 bits of Unix milliseconds, then version, variant and 74 random bits.
 *
 * <p>Keys made within the same millisecond are ordered randomly, which is enough to keep inserts
 * near the end of the index (ADR_010).
 */
@Component
public class UuidV7Generator implements IdGenerator {

  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  UuidV7Generator(Clock clock) {
    this.clock = clock;
  }

  @Override
  public UUID newId() {
    long millis = clock.millis();
    long mostSignificant = (millis << 16) | 0x7000L | (random.nextLong() & 0x0FFFL);
    long leastSignificant = (random.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL) | 0x8000_0000_0000_0000L;
    return new UUID(mostSignificant, leastSignificant);
  }
}
