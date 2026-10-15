package com.marketplace.shared;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The account that sent the current request, taken from its access token.
 *
 * @param sellerId the seller the account belongs to; only for seller accounts
 * @param roles role names without the account type, for example {@code ADMIN}
 */
public record AuthenticatedAccount(
    AccountType type,
    UUID id,
    @Nullable UUID sellerId,
    Set<String> roles,
    boolean mustChangePassword) {

  public AuthenticatedAccount {
    roles = Set.copyOf(roles);
  }

  /** The account of the current request, if the request carries a valid access token. */
  public static Optional<AuthenticatedAccount> current() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.getPrincipal() instanceof AuthenticatedAccount account) {
      return Optional.of(account);
    }
    return Optional.empty();
  }

  /**
   * The account of the current request, for code behind a path that security already restricts to
   * authenticated accounts.
   */
  public static AuthenticatedAccount require() {
    return current()
        .orElseThrow(() -> new IllegalStateException("The request is not authenticated"));
  }
}
