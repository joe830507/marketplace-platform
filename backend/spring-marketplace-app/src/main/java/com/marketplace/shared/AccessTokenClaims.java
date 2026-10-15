package com.marketplace.shared;

/**
 * Claims of the access token (ADR_009). The account module issues the token and the shared security
 * configuration verifies it, so both use these names.
 *
 * <p>The token carries no personal data: only identifiers, the account type and roles.
 */
public final class AccessTokenClaims {

  public static final String ISSUER = "marketplace-platform";
  public static final String ACCOUNT_TYPE = "account_type";
  public static final String SELLER_ID = "seller_id";
  public static final String ROLES = "roles";

  /** Present and true only for accounts that still use their initial password (設計_005 2.7). */
  public static final String MUST_CHANGE_PASSWORD = "must_change_password";

  private AccessTokenClaims() {}
}
