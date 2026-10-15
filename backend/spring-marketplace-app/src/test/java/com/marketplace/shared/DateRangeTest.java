package com.marketplace.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Date ranges of lists that keep growing (需求 3.11): Taiwan dates, 30 days by default, 1 year at
 * most.
 */
class DateRangeTest {

  /** 2026-10-15 00:30 in Taiwan is still 10-14 in UTC. */
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-14T16:30:00Z"), ZoneOffset.UTC);

  @Test
  @DisplayName("Q4 未指定日期區間：預設最近 30 天，以台灣日期計算")
  void lastThirtyDays() {
    DateRange range = DateRange.of(null, null, CLOCK);

    assertThat(range.to()).isEqualTo(LocalDate.parse("2026-10-15"));
    assertThat(range.from()).isEqualTo(LocalDate.parse("2026-09-16"));
    assertThat(range.start()).isEqualTo(Instant.parse("2026-09-15T16:00:00Z"));
    assertThat(range.endExclusive()).isEqualTo(Instant.parse("2026-10-15T16:00:00Z"));
  }

  @Test
  @DisplayName("Q4 日期區間過長：超過 1 年被拒絕，剛好 1 年可以")
  void atMostOneYear() {
    LocalDate from = LocalDate.parse("2025-10-16");

    assertThat(DateRange.of(from, LocalDate.parse("2026-10-15"), CLOCK).from()).isEqualTo(from);
    assertThatThrownBy(() -> DateRange.of(from, LocalDate.parse("2026-10-16"), CLOCK))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
  }

  @Test
  @DisplayName("起始日期晚於結束日期：被拒絕")
  void reversed() {
    assertThatThrownBy(
            () -> DateRange.of(LocalDate.parse("2026-10-15"), LocalDate.parse("2026-10-14"), CLOCK))
        .isInstanceOf(ApiException.class);
  }
}
