package com.marketplace.shared.internal.support;

import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the clock that all code uses to read the current time (設計_003 6).
 *
 * <p>This is the only place allowed to touch the system clock; tests replace the bean with a clock
 * they can move forward.
 *
 * <p>The clock ticks in microseconds, the precision of PostgreSQL's {@code timestamptz}, so a time
 * read back from the database equals the one the code wrote.
 */
@Configuration(proxyBeanMethods = false)
public class TimeConfiguration {

  @Bean
  Clock clock() {
    return Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000));
  }
}
