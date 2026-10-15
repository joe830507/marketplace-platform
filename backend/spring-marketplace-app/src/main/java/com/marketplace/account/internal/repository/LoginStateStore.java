package com.marketplace.account.internal.repository;

import com.marketplace.shared.AccountType;
import com.marketplace.shared.Actor;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Changes the failed-login count and lock with single conditional updates (ADR_004), so concurrent
 * wrong passwords are all counted and the account is locked exactly once.
 */
@Component
public class LoginStateStore {

  /** The login state as audit records show it. */
  public record LoginState(int failedLoginCount, @Nullable Instant lockedUntil) {}

  private final JdbcClient jdbc;

  LoginStateStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Counts a wrong password and locks the account when the count reaches {@code maxFailures}.
   *
   * @return the new state, or empty if the account is already locked
   */
  public Optional<LoginState> recordFailure(
      AccountType type, UUID id, int maxFailures, Instant lockUntil, Instant now) {
    return jdbc.sql(
            """
            UPDATE account.%s
            SET failed_login_count = failed_login_count + 1,
                locked_until = CASE WHEN failed_login_count + 1 >= :maxFailures
                                    THEN :lockUntil END,
                updated_at = :now, updated_by = :actor, version = version + 1
            WHERE id = :id AND locked_until IS NULL
            RETURNING failed_login_count, locked_until
            """
                .formatted(table(type)))
        .param("maxFailures", maxFailures)
        .param("lockUntil", utc(lockUntil))
        .param("now", utc(now))
        .param("actor", Actor.ANONYMOUS.value())
        .param("id", id)
        .query(
            (rs, row) -> {
              OffsetDateTime lockedUntil = rs.getObject("locked_until", OffsetDateTime.class);
              return new LoginState(
                  rs.getInt("failed_login_count"),
                  lockedUntil == null ? null : lockedUntil.toInstant());
            })
        .optional();
  }

  /**
   * Clears a lock that has expired. Only one of several concurrent logins succeeds.
   *
   * @return whether this call cleared it
   */
  public boolean unlock(AccountType type, UUID id, Instant expiredLock, Actor actor, Instant now) {
    return jdbc.sql(
                """
                UPDATE account.%s
                SET failed_login_count = 0, locked_until = NULL,
                    updated_at = :now, updated_by = :actor, version = version + 1
                WHERE id = :id AND locked_until = :expiredLock
                """
                    .formatted(table(type)))
            .param("now", utc(now))
            .param("actor", actor.value())
            .param("id", id)
            .param("expiredLock", utc(expiredLock))
            .update()
        == 1;
  }

  /** Resets the count after a successful login; writes nothing if it is already zero. */
  public void resetFailures(AccountType type, UUID id, Instant now) {
    jdbc.sql(
            """
            UPDATE account.%s
            SET failed_login_count = 0,
                updated_at = :now, updated_by = :actor, version = version + 1
            WHERE id = :id AND failed_login_count > 0
            """
                .formatted(table(type)))
        .param("now", utc(now))
        .param("actor", Actor.account(type, id).value())
        .param("id", id)
        .update();
  }

  public Optional<Instant> lockedUntil(AccountType type, UUID id) {
    return jdbc.sql("SELECT locked_until FROM account.%s WHERE id = :id".formatted(table(type)))
        .param("id", id)
        .query((rs, row) -> rs.getObject("locked_until", OffsetDateTime.class))
        .optional()
        .map(OffsetDateTime::toInstant);
  }

  private static String table(AccountType type) {
    return switch (type) {
      case MEMBER -> "member";
      case SELLER_USER -> "seller_user";
      case OPERATOR -> "operator_user";
    };
  }

  private static OffsetDateTime utc(Instant instant) {
    return instant.atOffset(ZoneOffset.UTC);
  }
}
