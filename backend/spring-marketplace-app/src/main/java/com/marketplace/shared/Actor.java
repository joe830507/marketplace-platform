package com.marketplace.shared;

import java.util.UUID;

/**
 * Who made a change, written to the audit columns and audit records as {@code TYPE:identifier}
 * (設計_004 2.3), for example {@code MEMBER:0192...} or {@code JOB:逾時取消}.
 */
public record Actor(String value) {

  /** A request without an access token, for example a failed login. */
  public static final Actor ANONYMOUS = new Actor("ANONYMOUS");

  public static Actor account(AccountType type, UUID id) {
    return new Actor(type.name() + ":" + id);
  }

  /** A change the system makes on its own, outside requests and batch jobs. */
  public static Actor system(String reason) {
    return new Actor("SYSTEM:" + reason);
  }

  public static Actor job(String name) {
    return new Actor("JOB:" + name);
  }

  /** The account of the current request, or {@link #ANONYMOUS} if there is none. */
  public static Actor current() {
    return AuthenticatedAccount.current()
        .map(account -> account(account.type(), account.id()))
        .orElse(ANONYMOUS);
  }

  @Override
  public String toString() {
    return value;
  }
}
