package com.marketplace.shared;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Dates are decided in Taiwan time (UTC+8, 需求 3.10); the database stores UTC (設計_003 6). */
public final class BusinessTime {

  public static final ZoneId ZONE = ZoneId.of("Asia/Taipei");

  private BusinessTime() {}

  public static LocalDate today(Clock clock) {
    return LocalDate.ofInstant(clock.instant(), ZONE);
  }

  public static LocalDate dateOf(Instant instant) {
    return LocalDate.ofInstant(instant, ZONE);
  }

  /** The first instant of the date in Taiwan. */
  public static Instant startOf(LocalDate date) {
    return date.atStartOfDay(ZONE).toInstant();
  }
}
