package com.marketplace.testsupport;

import com.marketplace.shared.AccountType;
import com.marketplace.shared.IdGenerator;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Creates accounts for tests directly in the database, each with a new ID and a unique username
 * (設計_006 4.2).
 *
 * <p>Members and sellers accept the latest terms when they are created, as registration does, so
 * that tests of other features are not stopped by the terms gate (需求 3.12).
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
    TestAccount member = new TestAccount(AccountType.MEMBER, id, username, PASSWORD, null);
    acceptLatestTerms(member);
    return member;
  }

  /** A seller account of a new active seller. */
  public TestAccount sellerUser(String role, boolean mustChangePassword) {
    UUID sellerId = seller("THIRD_PARTY");
    return sellerUserOf(sellerId, role, mustChangePassword);
  }

  /** A new active seller without accounts. */
  public UUID seller(String sellerType) {
    UUID sellerId = ids.newId();
    jdbc.sql(
            """
            INSERT INTO account.seller (id, name, seller_type, status,
              created_at, created_by, updated_at, updated_by, version)
            VALUES (:id, :name, :sellerType, 'ACTIVE', :now, 'TEST', :now, 'TEST', 0)
            """)
        .param("id", sellerId)
        .param("name", "Seller " + sellerId)
        .param("sellerType", sellerType)
        .param("now", now())
        .update();
    return sellerId;
  }

  /** An account of an existing seller; it accepts the latest seller terms for the seller. */
  public TestAccount sellerUserOf(UUID sellerId, String role, boolean mustChangePassword) {
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
    TestAccount sellerUser =
        new TestAccount(AccountType.SELLER_USER, id, username, PASSWORD, sellerId);
    acceptLatestTerms(sellerUser);
    return sellerUser;
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

  /**
   * Accepts the latest versions of the terms the account needs: the member documents for a member,
   * the seller terms on behalf of the seller for a seller account.
   */
  public void acceptLatestTerms(TestAccount account) {
    List<String> types =
        switch (account.type()) {
          case MEMBER -> List.of("TERMS_OF_SERVICE", "PRIVACY_POLICY");
          case SELLER_USER -> List.of("SELLER_TERMS");
          case OPERATOR -> List.of();
        };
    for (String type : types) {
      jdbc.sql(
              """
              SELECT id FROM account.terms_document WHERE document_type = :type
              ORDER BY version DESC LIMIT 1
              """)
          .param("type", type)
          .query(UUID.class)
          .optional()
          .ifPresent(
              documentId ->
                  jdbc.sql(
                          """
                          INSERT INTO account.terms_consent (id, account_type, account_id,
                            seller_id, terms_document_id, consented_at)
                          VALUES (:id, :accountType, :accountId, :sellerId, :documentId, :now)
                          ON CONFLICT DO NOTHING
                          """)
                      .param("id", ids.newId())
                      .param("accountType", account.type().name())
                      .param("accountId", account.id())
                      .param("sellerId", account.sellerId())
                      .param("documentId", documentId)
                      .param("now", now())
                      .update());
    }
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
