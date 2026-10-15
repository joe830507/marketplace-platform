package com.marketplace.testsupport;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A clock that tests move forward by hand, for example "31 minutes later" (設計_006 4.1).
 *
 * <p>It only moves forward, so later tests never see data from the future (設計_006 4.2).
 */
public final class MutableClock extends Clock {

  private final AtomicReference<Instant> instant;
  private final ZoneId zone;

  public MutableClock(Instant start, ZoneId zone) {
    this(new AtomicReference<>(start), zone);
  }

  private MutableClock(AtomicReference<Instant> instant, ZoneId zone) {
    this.instant = instant;
    this.zone = zone;
  }

  public void advance(Duration duration) {
    if (duration.isNegative()) {
      throw new IllegalArgumentException("The clock only moves forward: " + duration);
    }
    instant.updateAndGet(current -> current.plus(duration));
  }

  @Override
  public Instant instant() {
    return instant.get();
  }

  @Override
  public ZoneId getZone() {
    return zone;
  }

  /** Returns a view in another zone that shares the same time. */
  @Override
  public Clock withZone(ZoneId zone) {
    return new MutableClock(instant, zone);
  }
}
