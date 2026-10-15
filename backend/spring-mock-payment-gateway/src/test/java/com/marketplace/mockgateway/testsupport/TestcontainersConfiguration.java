package com.marketplace.mockgateway.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Connects the application to a real PostgreSQL (設計_006 2.1).
 *
 * <p>One container is shared by the whole test run (設計_006 4.2), even when tests start different
 * Spring contexts. It is started on first use and removed by Testcontainers when the JVM exits.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

  /** Set by Surefire and Failsafe from the {@code postgres.image} Maven property. */
  private static final String IMAGE = System.getProperty("postgres.image", "postgres:18.6");

  private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(IMAGE);

  static {
    POSTGRES.start();
  }

  @Bean
  DynamicPropertyRegistrar postgresProperties() {
    return registry -> {
      registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
      registry.add("spring.datasource.username", POSTGRES::getUsername);
      registry.add("spring.datasource.password", POSTGRES::getPassword);
    };
  }
}
