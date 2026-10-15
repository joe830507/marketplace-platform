package com.marketplace.account.internal.config;

import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Token lifetimes and the login lock (ADR_009, 需求 3.13). Kept as settings so tests and rule notices
 * use the same values as the code (設計_003 6).
 */
@ConfigurationProperties("marketplace.auth")
public record AuthProperties(
    @DefaultValue("15m") Duration accessTokenTtl,
    @DefaultValue("7d") Duration refreshTokenTtl,
    @DefaultValue("5") int maxFailedLogins,
    @DefaultValue("15m") Duration lockDuration,
    @DefaultValue InitialOperator initialOperator) {

  /**
   * The first operator administrator, created at startup while no operator exists (設計_005 3.4).
   *
   * @param password the initial password, given at deployment and never kept in the repository;
   *     without it no account is created
   */
  public record InitialOperator(
      @DefaultValue("admin") String username, @Nullable String password) {}
}
