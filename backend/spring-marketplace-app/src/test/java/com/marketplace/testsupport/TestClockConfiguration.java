package com.marketplace.testsupport;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Replaces the application's clock with a {@link MutableClock} that tests can move forward. Like
 * the application's clock it starts on a whole microsecond, the database's precision.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestClockConfiguration {

  @Bean
  @Primary
  MutableClock mutableClock() {
    return new MutableClock(Instant.now().truncatedTo(ChronoUnit.MICROS), ZoneOffset.UTC);
  }
}
