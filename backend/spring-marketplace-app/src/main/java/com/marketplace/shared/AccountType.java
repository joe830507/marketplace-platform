package com.marketplace.shared;

/** The three independent kinds of accounts (需求 2); each logs in under its own path prefix. */
public enum AccountType {
  MEMBER("member"),
  SELLER_USER("seller"),
  OPERATOR("ops");

  private final String pathSegment;

  AccountType(String pathSegment) {
    this.pathSegment = pathSegment;
  }

  /** The API path prefix this account type may call, for example {@code /api/v1/member}. */
  public String apiPrefix() {
    return "/api/v1/" + pathSegment;
  }

  /** The authority every authenticated request of this account type carries. */
  public String authority() {
    return "ACCOUNT_" + name();
  }

  /** The authority for one of this account type's roles, for example {@code OPERATOR_ADMIN}. */
  public String roleAuthority(String role) {
    return name() + "_" + role;
  }
}
