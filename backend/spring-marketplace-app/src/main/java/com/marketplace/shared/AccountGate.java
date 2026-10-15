package com.marketplace.shared;

import java.util.Optional;

/**
 * A condition an authenticated account must meet before it may call the API, for example accepting
 * the latest terms (需求 3.12). Modules provide gates as beans; security checks them after
 * authorization, once the account must no longer change its initial password.
 */
public interface AccountGate {

  /**
   * @param path the request path, so that a gate can let through the APIs that resolve it
   * @return the error to answer with, or empty to let the request through
   */
  Optional<ApiException> check(AuthenticatedAccount account, String path);
}
