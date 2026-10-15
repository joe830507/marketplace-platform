package com.marketplace.testsupport;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

/**
 * Starts the whole application against a real database, with MockMvc for API tests and the test
 * clock (設計_006 2, 4.1).
 *
 * <p>Module and API tests all use this configuration so they share one Spring context.
 *
 * <p>Tracing is configured as in production. Without {@code @AutoConfigureTracing}, Spring Boot
 * tests still create trace IDs but ignore the caller's {@code traceparent} header.
 *
 * <p>The database starts without operators, so the first operator administrator is created from the
 * settings at startup.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(
    properties =
        "marketplace.auth.initial-operator.password=" + AccountFixtures.INITIAL_OPERATOR_PASSWORD)
@AutoConfigureMockMvc
@AutoConfigureTracing
@Import({
  TestcontainersConfiguration.class,
  TestClockConfiguration.class,
  AccountFixtures.class,
  AuthTestClient.class
})
public @interface ApplicationTest {}
