package com.marketplace.testsupport;

import com.marketplace.shared.AccountType;
import com.marketplace.shared.IdGenerator;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Creates accounts for tests directly in the database, each with a new ID and a unique username
 * (設計_006 4.2), until the account management API exists.
 */
@TestComponent
public class AccountFixtures {

  public static final String PASSWORD = "Password-1234";

  /** The first operator administrator, created at startup from the settings (設計_005 3.4). */
  public static final String INITIAL_OPERATOR_USERNAME = "admin";

  public static final String INITIAL_OPERATOR_PASSWORD = "Initial-password-1234";

  /** A created account and the password it logs in with. */
  public record TestAccount(
      AccountType type, UUID id, String username, String password, @Nullable UUID sellerId) {}

  private final JdbcClient jdbc;
  private final IdGenerator ids;
  private final Clock clock;
  private final String passwordHash;

  AccountFixtures(JdbcClient jdbc, PasswordEncoder passwordEncoder, IdGenerator ids, Clock clock) {
    this.jdbc = jdbc;
    this.ids = ids;
    this.clock = clock;
    this.passwordHash = passwordEncoder.encode(PASSWORD);
  }

  public TestAccount member() {
    UUID id = ids.newId();
    String username = uniqueUsername("member");
    jdbc.sql(
            """
            INSERT INTO account.member (id, username, password_hash, display_name, status,
              created_at, created_by, updated_at, updated_by, version)
            VALUES (:id, :username, :passwordHash, 'Test member', 'ACTIVE',
              :now, 'TEST', :now, 'TEST', 0)
            """)
        .param("id", id)
        .param("username", username)
        .param("passwordHash", passwordHash)
        .param("now", now())
        .update();
    return new TestAccount(AccountType.MEMBER, id, username, PASSWORD, null);
  }

  /** A seller account of a new active seller. */
  public TestAccount sellerUser(String role, boolean mustChangePassword) {
    UUID sellerId = ids.newId();
    jdbc.sql(
            """
            INSERT INTO account.seller (id, name, seller_type, status,
              created_at, created_by, updated_at, updated_by, version)
            VALUES (:id, :name, 'THIRD_PARTY', 'ACTIVE', :now, 'TEST', :now, 'TEST', 0)
            """)
        .param("id", sellerId)
        .param("name", "Seller " + sellerId)
        .param("now", now())
        .update();

    UUID id = ids.newId();
    String username = uniqueUsername("seller");
    jdbc.sql(
            """
            INSERT INTO account.seller_user (id, seller_id, username, password_hash, role, status,
              must_change_password, created_at, created_by, updated_at, updated_by, version)
            VALUES (:id, :sellerId, :username, :passwordHash, :role, 'ACTIVE',
              :mustChangePassword, :now, 'TEST', :now, 'TEST', 0)
            """)
        .param("id", id)
        .param("sellerId", sellerId)
        .param("username", username)
        .param("passwordHash", passwordHash)
        .param("role", role)
        .param("mustChangePassword", mustChangePassword)
        .param("now", now())
        .update();
    return new TestAccount(AccountType.SELLER_USER, id, username, PASSWORD, sellerId);
  }

  public TestAccount operator(Set<String> roles, boolean mustChangePassword) {
    UUID id = ids.newId();
    String username = uniqueUsername("operator");
    jdbc.sql(
            """
            INSERT INTO account.operator_user (id, username, password_hash, status,
              must_change_password, created_at, created_by, updated_at, updated_by, version)
            VALUES (:id, :username, :passwordHash, 'ACTIVE',
              :mustChangePassword, :now, 'TEST', :now, 'TEST', 0)
            """)
        .param("id", id)
        .param("username", username)
        .param("passwordHash", passwordHash)
        .param("mustChangePassword", mustChangePassword)
        .param("now", now())
        .update();
    for (String role : roles) {
      jdbc.sql(
              """
              INSERT INTO account.operator_user_role (operator_user_id, role, created_at, created_by)
              VALUES (:id, :role, :now, 'TEST')
              """)
          .param("id", id)
          .param("role", role)
          .param("now", now())
          .update();
    }
    return new TestAccount(AccountType.OPERATOR, id, username, PASSWORD, null);
  }

  /** One account of each type, none of which has to change its password. */
  public TestAccount any(AccountType type) {
    return switch (type) {
      case MEMBER -> member();
      case SELLER_USER -> sellerUser("ADMIN", false);
      case OPERATOR -> operator(Set.of("ADMIN"), false);
    };
  }

  public void disable(TestAccount account) {
    String table =
        switch (account.type()) {
          case MEMBER -> "member";
          case SELLER_USER -> "seller_user";
          case OPERATOR -> "operator_user";
        };
    jdbc.sql("UPDATE account.%s SET status = 'DISABLED' WHERE id = :id".formatted(table))
        .param("id", account.id())
        .update();
  }

  public void disableSeller(UUID sellerId) {
    jdbc.sql("UPDATE account.seller SET status = 'DISABLED' WHERE id = :id")
        .param("id", sellerId)
        .update();
  }

  private OffsetDateTime now() {
    return clock.instant().atOffset(ZoneOffset.UTC);
  }

  private static String uniqueUsername(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 13);
  }
}
