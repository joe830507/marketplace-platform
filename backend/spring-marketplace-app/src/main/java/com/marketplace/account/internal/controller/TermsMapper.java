package com.marketplace.account.internal.controller;

import com.marketplace.account.TermsDocumentType;
import com.marketplace.account.internal.service.TermsService.ConsentView;
import com.marketplace.account.internal.service.TermsService.TermsView;
import com.marketplace.openapi.model.TermsConsentRecord;
import com.marketplace.openapi.model.TermsDocument;
import java.time.ZoneOffset;

/** Converts terms between the service views and the API models. */
final class TermsMapper {

  private TermsMapper() {}

  static TermsDocument toApi(TermsView terms) {
    return new TermsDocument(
        terms.id(),
        toApi(terms.type()),
        terms.version(),
        terms.content(),
        terms.publishedAt().atOffset(ZoneOffset.UTC));
  }

  static TermsConsentRecord toApi(ConsentView consent) {
    return new TermsConsentRecord(
            consent.document().id(),
            toApi(consent.document().type()),
            consent.document().version(),
            TermsConsentRecord.AccountTypeEnum.fromValue(consent.accountType().name()),
            consent.accountId(),
            consent.consentedAt().atOffset(ZoneOffset.UTC))
        .sellerId(consent.sellerId());
  }

  static com.marketplace.openapi.model.TermsDocumentType toApi(TermsDocumentType type) {
    return com.marketplace.openapi.model.TermsDocumentType.fromValue(type.name());
  }

  static TermsDocumentType fromApi(com.marketplace.openapi.model.TermsDocumentType type) {
    return TermsDocumentType.valueOf(type.getValue());
  }
}
