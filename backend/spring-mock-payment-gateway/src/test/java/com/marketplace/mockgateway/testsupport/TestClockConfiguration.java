package com.marketplace.mockgateway.testsupport;

import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Replaces the application's clock with a {@link MutableClock} that tests can move forward. */
@TestConfiguration(proxyBeanMethods = false)
public class TestClockConfiguration {

  @Bean
  @Primary
  MutableClock mutableClock() {
    return new MutableClock(Instant.now(), ZoneOffset.UTC);
  }
}
