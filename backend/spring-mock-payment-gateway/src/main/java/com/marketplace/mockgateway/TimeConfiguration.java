package com.marketplace.mockgateway;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the clock that all code uses to read the current time.
 *
 * <p>This is the only place allowed to touch the system clock; tests replace the bean with a clock
 * they can move forward.
 */
@Configuration(proxyBeanMethods = false)
class TimeConfiguration {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
