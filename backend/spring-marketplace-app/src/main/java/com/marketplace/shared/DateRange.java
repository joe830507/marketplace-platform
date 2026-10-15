package com.marketplace.shared;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * The date range of a list whose data keeps growing (需求 3.11): the last 30 days by default, and at
 * most one year. Dates are Taiwan dates and both ends are included.
 */
public record DateRange(LocalDate from, LocalDate to) {

  static final int DEFAULT_DAYS = 30;

  /**
   * Fills in what the request leaves out: no {@code to} means today, no {@code from} means 30 days
   * up to {@code to}.
   *
   * @throws ApiException {@code VALIDATION_FAILED} if the range is reversed or longer than a year
   */
  public static DateRange of(@Nullable LocalDate from, @Nullable LocalDate to, Clock clock) {
    LocalDate end = to == null ? BusinessTime.today(clock) : to;
    LocalDate start = from == null ? end.minusDays(DEFAULT_DAYS - 1) : from;
    if (start.isAfter(end)) {
      throw ApiException.invalidField("from", "起始日期不可晚於結束日期");
    }
    if (!end.isBefore(start.plusYears(1))) {
      throw ApiException.invalidField("to", "日期區間最長 1 年");
    }
    return new DateRange(start, end);
  }

  /** The first instant of the range. */
  public Instant start() {
    return BusinessTime.startOf(from);
  }

  /** The first instant after the range. */
  public Instant endExclusive() {
    return BusinessTime.startOf(to.plusDays(1));
  }
}
