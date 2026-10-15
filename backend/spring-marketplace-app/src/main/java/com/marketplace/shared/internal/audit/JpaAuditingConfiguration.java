package com.marketplace.shared.internal.audit;

import com.marketplace.shared.Actor;
import java.time.Clock;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** Fills the level-three audit columns of {@code AuditableEntity} (設計_004 2.3). */
@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(auditorAwareRef = "auditor", dateTimeProviderRef = "auditingTime")
public class JpaAuditingConfiguration {

  @Bean
  AuditorAware<String> auditor() {
    return () -> Optional.of(Actor.current().value());
  }

  /** Uses the injected clock, so audit times follow the test clock too. */
  @Bean
  DateTimeProvider auditingTime(Clock clock) {
    return () -> Optional.of(clock.instant());
  }
}
