package com.marketplace.account.internal.controller;

import com.marketplace.account.internal.service.TermsService;
import com.marketplace.shared.AccountGate;
import com.marketplace.shared.ApiException;
import com.marketplace.shared.AuthenticatedAccount;
import com.marketplace.shared.ErrorCode;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * After a new version of the terms is published, members and sellers must accept it before using
 * features that need an identity (需求 3.12). Logging in and accepting the terms stay open; reading
 * the terms and browsing products are public anyway.
 */
@Component
public class TermsConsentGate implements AccountGate {

  private final TermsService terms;

  TermsConsentGate(TermsService terms) {
    this.terms = terms;
  }

  @Override
  public Optional<ApiException> check(AuthenticatedAccount account, String path) {
    String prefix = account.type().apiPrefix();
    if (path.startsWith(prefix + "/auth/")
        || path.equals(prefix + "/terms-consents")
        || !terms.consentRequired(account)) {
      return Optional.empty();
    }
    return Optional.of(new ApiException(ErrorCode.TERMS_CONSENT_REQUIRED, "條款已更新，請先同意最新版本的條款"));
  }
}
