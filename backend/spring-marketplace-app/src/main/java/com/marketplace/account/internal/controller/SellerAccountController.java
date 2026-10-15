package com.marketplace.account.internal.controller;

import com.marketplace.account.internal.service.TermsService;
import com.marketplace.openapi.api.SellerAccountApi;
import com.marketplace.openapi.model.TermsConsentRequest;
import com.marketplace.shared.AuthenticatedAccount;
import com.marketplace.shared.RoleChecks;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

/** Seller terms, accepted by a seller's administrator on behalf of the seller (需求 3.12). */
@RestController
public class SellerAccountController implements SellerAccountApi {

  private final TermsService terms;

  SellerAccountController(TermsService terms) {
    this.terms = terms;
  }

  @Override
  @PreAuthorize(RoleChecks.SELLER_ADMIN)
  public ResponseEntity<Void> consentSellerTerms(TermsConsentRequest termsConsentRequest) {
    terms.consent(AuthenticatedAccount.require(), termsConsentRequest.getTermsDocumentId());
    return ResponseEntity.noContent().build();
  }
}
