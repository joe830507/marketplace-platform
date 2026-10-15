package com.marketplace.account.internal.repository;

import com.marketplace.shared.Actor;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Creates the first operator administrator with SQL, so the audit columns name the system instead
 * of the request's actor, and several instances starting at once create only one account.
 */
@Repository
public class InitialOperatorStore {

  private final JdbcClient jdbc;

  InitialOperatorStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public boolean anyOperatorExists() {
    return jdbc.sql("SELECT EXISTS (SELECT 1 FROM account.operator_user)")
        .query(Boolean.class)
        .single();
  }

  /**
   * Inserts an active administrator who must change the password at first login.
   *
   * @return false if an account with the same username already exists
   */
  public boolean insertAdministrator(
      UUID id, String username, String passwordHash, Instant now, Actor actor) {
    OffsetDateTime at = now.atOffset(ZoneOffset.UTC);
    int inserted =
        jdbc.sql(
                """
                INSERT INTO account.operator_user (id, username, password_hash, status,
                  must_change_password, created_at, created_by, updated_at, updated_by, version)
                VALUES (:id, :username, :passwordHash, 'ACTIVE', true, :at, :actor, :at, :actor, 0)
                ON CONFLICT DO NOTHING
                """)
            .param("id", id)
            .param("username", username)
            .param("passwordHash", passwordHash)
            .param("at", at)
            .param("actor", actor.value())
            .update();
    if (inserted == 0) {
      return false;
    }
    jdbc.sql(
            """
            INSERT INTO account.operator_user_role (operator_user_id, role, created_at, created_by)
            VALUES (:id, 'ADMIN', :at, :actor)
            """)
        .param("id", id)
        .param("at", at)
        .param("actor", actor.value())
        .update();
    return true;
  }
}
