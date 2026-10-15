package com.marketplace.account.internal.repository;

import com.marketplace.shared.AccountType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Stores refresh tokens by their SHA-256 hash (設計_004 3). A token is random, so a fast hash is
 * enough; there is nothing to guess as with passwords.
 */
@Component
public class RefreshTokenStore {

  public record StoredToken(
      UUID id,
      AccountType accountType,
      UUID accountId,
      Instant expiresAt,
      @Nullable Instant revokedAt,
      @Nullable UUID replacedBy) {}

  private final JdbcClient jdbc;
  private final SecureRandom random = new SecureRandom();

  RefreshTokenStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Saves a new token for the account.
   *
   * @return the token itself, which only the client keeps
   */
  public String create(UUID id, AccountType type, UUID accountId, Instant expiresAt, Instant now) {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    jdbc.sql(
            """
            INSERT INTO account.refresh_token
              (id, account_type, account_id, token_hash, expires_at, created_at)
            VALUES (:id, :accountType, :accountId, :tokenHash, :expiresAt, :createdAt)
            """)
        .param("id", id)
        .param("accountType", type.name())
        .param("accountId", accountId)
        .param("tokenHash", hash(token))
        .param("expiresAt", utc(expiresAt))
        .param("createdAt", utc(now))
        .update();
    return token;
  }

  public Optional<StoredToken> find(String token) {
    return jdbc.sql(
            """
            SELECT id, account_type, account_id, expires_at, revoked_at, replaced_by
            FROM account.refresh_token
            WHERE token_hash = :tokenHash
            """)
        .param("tokenHash", hash(token))
        .query(
            (rs, row) -> {
              OffsetDateTime revokedAt = rs.getObject("revoked_at", OffsetDateTime.class);
              return new StoredToken(
                  rs.getObject("id", UUID.class),
                  AccountType.valueOf(rs.getString("account_type")),
                  rs.getObject("account_id", UUID.class),
                  rs.getObject("expires_at", OffsetDateTime.class).toInstant(),
                  revokedAt == null ? null : revokedAt.toInstant(),
                  rs.getObject("replaced_by", UUID.class));
            })
        .optional();
  }

  /**
   * Revokes a token because a new one replaces it.
   *
   * @return false if the token was already revoked, for example by a concurrent refresh
   */
  public boolean replace(UUID id, UUID replacedBy, Instant now) {
    return jdbc.sql(
                """
                UPDATE account.refresh_token
                SET revoked_at = :now, replaced_by = :replacedBy
                WHERE id = :id AND revoked_at IS NULL
                """)
            .param("now", utc(now))
            .param("replacedBy", replacedBy)
            .param("id", id)
            .update()
        == 1;
  }

  /** Revokes every active token of the account (ADR_009). */
  public void revokeAll(AccountType type, UUID accountId, Instant now) {
    jdbc.sql(
            """
            UPDATE account.refresh_token
            SET revoked_at = :now
            WHERE account_type = :accountType AND account_id = :accountId AND revoked_at IS NULL
            """)
        .param("now", utc(now))
        .param("accountType", type.name())
        .param("accountId", accountId)
        .update();
  }

  private static String hash(String token) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static OffsetDateTime utc(Instant instant) {
    return instant.atOffset(ZoneOffset.UTC);
  }
}
