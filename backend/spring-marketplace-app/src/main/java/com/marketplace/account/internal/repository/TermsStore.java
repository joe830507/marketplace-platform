package com.marketplace.account.internal.repository;

import com.marketplace.account.TermsDocumentType;
import com.marketplace.shared.AccountType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Terms documents and consents (需求 3.12). Both are insert only: a new version is a new row, and a
 * consent is never changed.
 */
@Component
public class TermsStore {

  public record TermsDocument(
      UUID id, TermsDocumentType type, int version, String content, Instant publishedAt) {}

  public record Consent(
      TermsDocument document,
      AccountType accountType,
      UUID accountId,
      @Nullable UUID sellerId,
      Instant consentedAt) {}

  private static final String DOCUMENT_COLUMNS =
      "d.id, d.document_type, d.version, d.content, d.published_at";

  private final JdbcClient jdbc;

  TermsStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Publishes the next version of a document type. Publications of the same type are serialized
   * with a transaction-scoped advisory lock, so two of them cannot take the same version.
   */
  public TermsDocument publish(
      UUID id, TermsDocumentType type, String content, Instant now, String publishedBy) {
    jdbc.sql("SELECT pg_advisory_xact_lock(hashtext(:key))")
        .param("key", "account.terms_document:" + type.name())
        .query()
        .singleRow();
    int version =
        jdbc.sql(
                """
                SELECT coalesce(max(version), 0) + 1 FROM account.terms_document
                WHERE document_type = :type
                """)
            .param("type", type.name())
            .query(Integer.class)
            .single();
    jdbc.sql(
            """
            INSERT INTO account.terms_document
              (id, document_type, version, content, published_at, published_by)
            VALUES (:id, :type, :version, :content, :publishedAt, :publishedBy)
            """)
        .param("id", id)
        .param("type", type.name())
        .param("version", version)
        .param("content", content)
        .param("publishedAt", utc(now))
        .param("publishedBy", publishedBy)
        .update();
    return new TermsDocument(id, type, version, content, now);
  }

  public Optional<TermsDocument> find(UUID id) {
    return jdbc.sql(
            "SELECT " + DOCUMENT_COLUMNS + " FROM account.terms_document d WHERE d.id = :id")
        .param("id", id)
        .query(TermsStore::document)
        .optional();
  }

  public Optional<TermsDocument> findLatest(TermsDocumentType type) {
    return jdbc.sql(
            "SELECT "
                + DOCUMENT_COLUMNS
                + """
                 FROM account.terms_document d
                WHERE d.document_type = :type
                ORDER BY d.version DESC
                LIMIT 1
                """)
        .param("type", type.name())
        .query(TermsStore::document)
        .optional();
  }

  /** Records a consent; consenting to the same document again changes nothing. */
  public void consent(
      UUID id,
      AccountType accountType,
      UUID accountId,
      @Nullable UUID sellerId,
      UUID documentId,
      Instant now) {
    jdbc.sql(
            """
            INSERT INTO account.terms_consent
              (id, account_type, account_id, seller_id, terms_document_id, consented_at)
            VALUES (:id, :accountType, :accountId, :sellerId, :documentId, :consentedAt)
            ON CONFLICT (account_type, account_id, terms_document_id) DO NOTHING
            """)
        .param("id", id)
        .param("accountType", accountType.name())
        .param("accountId", accountId)
        .param("sellerId", sellerId)
        .param("documentId", documentId)
        .param("consentedAt", utc(now))
        .update();
  }

  /**
   * Whether the latest version of any of the types lacks a consent by the member. Types without a
   * published document need no consent.
   */
  public boolean memberLacksConsent(UUID memberId, Collection<TermsDocumentType> types) {
    return jdbc.sql(
            """
            SELECT EXISTS (
              SELECT 1 FROM (
                SELECT DISTINCT ON (document_type) id FROM account.terms_document
                WHERE document_type IN (:types)
                ORDER BY document_type, version DESC
              ) latest
              WHERE NOT EXISTS (
                SELECT 1 FROM account.terms_consent c
                WHERE c.terms_document_id = latest.id
                  AND c.account_type = 'MEMBER' AND c.account_id = :memberId))
            """)
        .param("types", types.stream().map(Enum::name).toList())
        .param("memberId", memberId)
        .query(Boolean.class)
        .single();
  }

  /** Whether the latest seller terms lack a consent given on behalf of the seller. */
  public boolean sellerLacksConsent(UUID sellerId) {
    return jdbc.sql(
            """
            SELECT EXISTS (
              SELECT 1 FROM (
                SELECT id FROM account.terms_document
                WHERE document_type = 'SELLER_TERMS'
                ORDER BY version DESC
                LIMIT 1
              ) latest
              WHERE NOT EXISTS (
                SELECT 1 FROM account.terms_consent c
                WHERE c.terms_document_id = latest.id AND c.seller_id = :sellerId))
            """)
        .param("sellerId", sellerId)
        .query(Boolean.class)
        .single();
  }

  /** Consents given by a member, or on behalf of a seller; newest first. */
  public List<Consent> findConsents(@Nullable UUID memberId, @Nullable UUID sellerId) {
    return jdbc.sql(
            "SELECT "
                + DOCUMENT_COLUMNS
                + """
                , c.account_type, c.account_id, c.seller_id, c.consented_at
                FROM account.terms_consent c
                JOIN account.terms_document d ON d.id = c.terms_document_id
                WHERE (CAST(:memberId AS uuid) IS NOT NULL
                       AND c.account_type = 'MEMBER' AND c.account_id = :memberId)
                   OR (CAST(:sellerId AS uuid) IS NOT NULL AND c.seller_id = :sellerId)
                ORDER BY c.consented_at DESC, d.document_type, d.version DESC
                """)
        .param("memberId", memberId)
        .param("sellerId", sellerId)
        .query(
            (rs, row) ->
                new Consent(
                    document(rs, row),
                    AccountType.valueOf(rs.getString("account_type")),
                    rs.getObject("account_id", UUID.class),
                    rs.getObject("seller_id", UUID.class),
                    rs.getObject("consented_at", OffsetDateTime.class).toInstant()))
        .list();
  }

  private static TermsDocument document(ResultSet rs, int row) throws SQLException {
    return new TermsDocument(
        rs.getObject("id", UUID.class),
        TermsDocumentType.valueOf(rs.getString("document_type")),
        rs.getInt("version"),
        rs.getString("content"),
        rs.getObject("published_at", OffsetDateTime.class).toInstant());
  }

  private static OffsetDateTime utc(Instant instant) {
    return instant.atOffset(ZoneOffset.UTC);
  }
}
