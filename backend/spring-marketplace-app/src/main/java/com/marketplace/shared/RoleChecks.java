package com.marketplace.shared;

/**
 * Role checks for {@code PreAuthorize} on controllers (需求 3.13). Each role is an authority named by
 * {@link AccountType#roleAuthority(String)}; the path prefix already guarantees the account type.
 */
public final class RoleChecks {

  /** 營運管理者. */
  public static final String OPERATOR_ADMIN = "hasAuthority('OPERATOR_ADMIN')";

  /** 賣家的管理者. */
  public static final String SELLER_ADMIN = "hasAuthority('SELLER_USER_ADMIN')";

  private RoleChecks() {}
}
