package com.marketplace.account.internal.service;

import com.marketplace.account.TermsDocumentType;
import com.marketplace.account.internal.repository.TermsStore;
import com.marketplace.account.internal.repository.TermsStore.TermsDocument;
import com.marketplace.shared.AccountType;
import com.marketplace.shared.Actor;
import com.marketplace.shared.ApiException;
import com.marketplace.shared.AuditLog;
import com.marketplace.shared.AuthenticatedAccount;
import com.marketplace.shared.ErrorCode;
import com.marketplace.shared.IdGenerator;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Terms documents with versions, and consents to them (需求 3.12).
 *
 * <p>Members accept the terms of service and the privacy policy; a seller's administrator accepts
 * the seller terms on behalf of the seller. After a new version is published, the account must
 * accept it before using features that need an identity.
 */
@Service
public class TermsService {

  /** The documents every member accepts. */
  static final Set<TermsDocumentType> MEMBER_DOCUMENTS =
      EnumSet.of(TermsDocumentType.TERMS_OF_SERVICE, TermsDocumentType.PRIVACY_POLICY);

  public record TermsView(
      UUID id, TermsDocumentType type, int version, String content, Instant publishedAt) {}

  public record ConsentView(
      TermsView document,
      AccountType accountType,
      UUID accountId,
      @Nullable UUID sellerId,
      Instant consentedAt) {}

  private final TermsStore terms;
  private final AuditLog auditLog;
  private final IdGenerator ids;
  private final Clock clock;

  TermsService(TermsStore terms, AuditLog auditLog, IdGenerator ids, Clock clock) {
    this.terms = terms;
    this.auditLog = auditLog;
    this.ids = ids;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public TermsView latest(TermsDocumentType type) {
    return terms
        .findLatest(type)
        .map(TermsService::view)
        .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "此類條款尚未發布"));
  }

  /** Any version of a document, including earlier ones (需求 3.12). */
  @Transactional(readOnly = true)
  public TermsView find(UUID id) {
    return view(document(id));
  }

  /** Publishes the next version; earlier versions stay readable (需求 3.12). */
  @Transactional
  public TermsView publish(TermsDocumentType type, String content) {
    Actor actor = Actor.current();
    TermsDocument document =
        terms.publish(ids.newId(), type, content, clock.instant(), actor.value());
    auditLog.record(
        actor,
        "PUBLISH_TERMS",
        "TERMS_DOCUMENT",
        document.id().toString(),
        null,
        Map.of("documentType", type.name(), "version", document.version()));
    return view(document);
  }

  /**
   * Checks that a new member accepts exactly the latest versions of the member documents.
   *
   * @throws ApiException {@code TERMS_NOT_LATEST} otherwise
   */
  @Transactional(readOnly = true)
  public void requireLatestMemberDocuments(Collection<UUID> documentIds) {
    for (TermsDocumentType type : MEMBER_DOCUMENTS) {
      TermsDocument latest =
          terms.findLatest(type).orElseThrow(() -> notLatest("平台尚未發布服務條款與隱私權政策"));
      if (!documentIds.contains(latest.id())) {
        throw notLatest("須同意最新版本的服務條款與隱私權政策");
      }
    }
    for (UUID id : documentIds) {
      TermsDocument document = terms.find(id).orElseThrow(() -> notLatest("條款不存在"));
      if (!isLatestFor(AccountType.MEMBER, document)) {
        throw notLatest("只能同意最新版本的會員條款");
      }
    }
  }

  /** Records a new member's consents, after {@link #requireLatestMemberDocuments}. */
  @Transactional
  public void recordMemberConsents(UUID memberId, Collection<UUID> documentIds) {
    Instant now = clock.instant();
    for (UUID documentId : Set.copyOf(documentIds)) {
      terms.consent(ids.newId(), AccountType.MEMBER, memberId, null, documentId, now);
    }
  }

  /**
   * Accepts the latest version of a document: members accept their own documents, a seller's
   * administrator the seller terms on behalf of the seller. Accepting again changes nothing.
   */
  @Transactional
  public void consent(AuthenticatedAccount account, UUID documentId) {
    TermsDocument document = document(documentId);
    if (!isLatestFor(account.type(), document)) {
      throw notLatest("只能同意最新版本、且適用於此帳號的條款");
    }
    UUID sellerId = account.type() == AccountType.SELLER_USER ? account.sellerId() : null;
    terms.consent(ids.newId(), account.type(), account.id(), sellerId, documentId, clock.instant());
  }

  /**
   * Whether the account must accept a newer version before using features that need an identity.
   * Members need the latest member documents; seller accounts need their seller to have accepted
   * the latest seller terms.
   */
  @Transactional(readOnly = true)
  public boolean consentRequired(AuthenticatedAccount account) {
    return switch (account.type()) {
      case MEMBER -> terms.memberLacksConsent(account.id(), MEMBER_DOCUMENTS);
      case SELLER_USER -> terms.sellerLacksConsent(account.sellerId());
      case OPERATOR -> false;
    };
  }

  /**
   * Consents a member gave, or that administrators gave on behalf of a seller, newest first (需求
   * 3.12).
   *
   * @throws ApiException {@code VALIDATION_FAILED} unless exactly one of the two is given
   */
  @Transactional(readOnly = true)
  public List<ConsentView> consents(@Nullable UUID memberId, @Nullable UUID sellerId) {
    if ((memberId == null) == (sellerId == null)) {
      throw ApiException.invalidField("memberId", "memberId 與 sellerId 須指定其中一個");
    }
    return terms.findConsents(memberId, sellerId).stream()
        .map(
            consent ->
                new ConsentView(
                    view(consent.document()),
                    consent.accountType(),
                    consent.accountId(),
                    consent.sellerId(),
                    consent.consentedAt()))
        .toList();
  }

  private TermsDocument document(UUID id) {
    return terms.find(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "條款不存在"));
  }

  private boolean isLatestFor(AccountType type, TermsDocument document) {
    boolean forType =
        switch (type) {
          case MEMBER -> MEMBER_DOCUMENTS.contains(document.type());
          case SELLER_USER -> document.type() == TermsDocumentType.SELLER_TERMS;
          case OPERATOR -> false;
        };
    return forType
        && terms
            .findLatest(document.type())
            .map(latest -> latest.id().equals(document.id()))
            .orElse(false);
  }

  private static TermsView view(TermsDocument document) {
    return new TermsView(
        document.id(),
        document.type(),
        document.version(),
        document.content(),
        document.publishedAt());
  }

  private static ApiException notLatest(String detail) {
    return new ApiException(ErrorCode.TERMS_NOT_LATEST, detail);
  }
}
