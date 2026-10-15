package com.marketplace.account.internal.service;

import com.marketplace.account.internal.config.AuthProperties;
import com.marketplace.account.internal.entity.LoginAccount;
import com.marketplace.account.internal.entity.Seller;
import com.marketplace.account.internal.repository.LoginAccountFinder;
import com.marketplace.account.internal.repository.LoginStateStore;
import com.marketplace.account.internal.repository.LoginStateStore.LoginState;
import com.marketplace.account.internal.repository.RefreshTokenStore;
import com.marketplace.account.internal.repository.RefreshTokenStore.StoredToken;
import com.marketplace.account.internal.repository.SellerRepository;
import com.marketplace.shared.AccountType;
import com.marketplace.shared.Actor;
import com.marketplace.shared.ApiException;
import com.marketplace.shared.AuditLog;
import com.marketplace.shared.AuthenticatedAccount;
import com.marketplace.shared.ErrorCode;
import com.marketplace.shared.IdGenerator;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login, token refresh, logout and password change for all three account types (需求 3.13, ADR_009,
 * 設計_005 2.7).
 *
 * <p>Methods that fail but must keep what they wrote, such as a counted failure, a lock or a
 * revocation, do not roll back on {@link ApiException}.
 */
@Service
public class AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthService.class);

  /** Who unlocks an account whose lock has expired; recorded at the next login attempt. */
  private static final Actor LOCK_EXPIRY = Actor.system("鎖定期滿");

  public record IssuedTokens(String accessToken, long expiresInSeconds, String refreshToken) {}

  private final LoginAccountFinder accounts;
  private final SellerRepository sellers;
  private final LoginStateStore loginState;
  private final RefreshTokenStore refreshTokens;
  private final AccessTokenIssuer accessTokens;
  private final PasswordEncoder passwordEncoder;
  private final AuditLog auditLog;
  private final IdGenerator ids;
  private final Clock clock;
  private final AuthProperties properties;

  /** Compared against when the username does not exist, so the response takes as long. */
  private final String unknownAccountHash;

  AuthService(
      LoginAccountFinder accounts,
      SellerRepository sellers,
      LoginStateStore loginState,
      RefreshTokenStore refreshTokens,
      AccessTokenIssuer accessTokens,
      PasswordEncoder passwordEncoder,
      AuditLog auditLog,
      IdGenerator ids,
      Clock clock,
      AuthProperties properties) {
    this.accounts = accounts;
    this.sellers = sellers;
    this.loginState = loginState;
    this.refreshTokens = refreshTokens;
    this.accessTokens = accessTokens;
    this.passwordEncoder = passwordEncoder;
    this.auditLog = auditLog;
    this.ids = ids;
    this.clock = clock;
    this.properties = properties;
    this.unknownAccountHash = passwordEncoder.encode(UUID.randomUUID().toString());
  }

  /**
   * Checks the password. Five failures in a row lock the account for 15 minutes; a success resets
   * the count (需求 3.13).
   */
  @Transactional(noRollbackFor = ApiException.class)
  public IssuedTokens login(AccountType type, String username, String password) {
    Instant now = clock.instant();
    Optional<? extends LoginAccount> found = accounts.findByUsername(type, username);
    if (found.isEmpty()) {
      passwordEncoder.matches(password, unknownAccountHash);
      throw invalidCredentials();
    }
    LoginAccount account = found.get();

    Instant lockedUntil = account.lockedUntil();
    if (lockedUntil != null) {
      if (now.isBefore(lockedUntil)) {
        throw locked(lockedUntil);
      }
      if (loginState.unlock(type, account.id(), lockedUntil, LOCK_EXPIRY, now)) {
        auditLog.record(
            LOCK_EXPIRY,
            "UNLOCK_ACCOUNT",
            type.name(),
            account.id().toString(),
            new LoginState(account.failedLoginCount(), lockedUntil),
            new LoginState(0, null));
      }
    }

    if (!passwordEncoder.matches(password, account.passwordHash())) {
      throw recordFailure(type, account.id(), now);
    }
    if (!canSignIn(account)) {
      throw invalidCredentials();
    }
    loginState.resetFailures(type, account.id(), now);
    return issue(account, now);
  }

  /**
   * Exchanges a refresh token for new tokens; the old refresh token stops working at once
   * (ADR_009).
   *
   * <p>A token that was already exchanged may have been stolen, so using it again revokes every
   * refresh token of the account (設計_004 3).
   */
  @Transactional(noRollbackFor = ApiException.class)
  public IssuedTokens refresh(AccountType type, @Nullable String token) {
    Instant now = clock.instant();
    StoredToken stored =
        Optional.ofNullable(token)
            .flatMap(refreshTokens::find)
            .filter(found -> found.accountType() == type)
            .orElseThrow(AuthService::invalidRefreshToken);

    if (stored.revokedAt() != null) {
      if (stored.replacedBy() != null) {
        revokeReusedToken(stored, now);
      }
      throw invalidRefreshToken();
    }
    if (!now.isBefore(stored.expiresAt())) {
      throw invalidRefreshToken();
    }
    LoginAccount account =
        accounts
            .findById(type, stored.accountId())
            .filter(this::canSignIn)
            .orElseThrow(AuthService::invalidRefreshToken);

    UUID replacement = ids.newId();
    if (!refreshTokens.replace(stored.id(), replacement, now)) {
      // Another request exchanged the same token in the meantime.
      revokeReusedToken(stored, now);
      throw invalidRefreshToken();
    }
    return issue(account, replacement, now);
  }

  /** Revokes every refresh token of the account the token belongs to (ADR_009). */
  @Transactional
  public void logout(AccountType type, @Nullable String token) {
    Optional.ofNullable(token)
        .flatMap(refreshTokens::find)
        .filter(found -> found.accountType() == type)
        .ifPresent(found -> refreshTokens.revokeAll(type, found.accountId(), clock.instant()));
  }

  /**
   * Changes the password and revokes every refresh token, so the account must log in again (設計_005
   * 2.7).
   */
  @Transactional
  public void changePassword(
      AuthenticatedAccount caller, String currentPassword, String newPassword) {
    LoginAccount account =
        accounts
            .findById(caller.type(), caller.id())
            .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED, "帳號不存在"));
    if (!passwordEncoder.matches(currentPassword, account.passwordHash())) {
      throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "目前的密碼不正確");
    }
    if (passwordEncoder.matches(newPassword, account.passwordHash())) {
      throw new ApiException(
          ErrorCode.VALIDATION_FAILED,
          "新密碼不可與目前的密碼相同",
          Map.of("errors", List.of(Map.of("field", "newPassword", "message", "不可與目前的密碼相同"))));
    }
    account.changePassword(passwordEncoder.encode(newPassword));
    refreshTokens.revokeAll(caller.type(), caller.id(), clock.instant());
  }

  /** Whether the account may use the platform: it is active, and so is its seller if it has one. */
  private boolean canSignIn(LoginAccount account) {
    if (!account.isActive()) {
      return false;
    }
    UUID sellerId = account.sellerId();
    return sellerId == null || sellers.findById(sellerId).map(Seller::isActive).orElse(false);
  }

  private ApiException recordFailure(AccountType type, UUID id, Instant now) {
    Optional<LoginState> state =
        loginState.recordFailure(
            type, id, properties.maxFailedLogins(), now.plus(properties.lockDuration()), now);
    if (state.isEmpty()) {
      // A concurrent attempt locked the account first.
      return locked(loginState.lockedUntil(type, id).orElse(now));
    }
    Instant lockedUntil = state.get().lockedUntil();
    if (lockedUntil == null) {
      return invalidCredentials();
    }
    auditLog.record(
        Actor.ANONYMOUS,
        "LOCK_ACCOUNT",
        type.name(),
        id.toString(),
        new LoginState(state.get().failedLoginCount() - 1, null),
        state.get());
    refreshTokens.revokeAll(type, id, now);
    return locked(lockedUntil);
  }

  private void revokeReusedToken(StoredToken stored, Instant now) {
    log.warn(
        "Refresh token {} was used after it had been exchanged; revoking all tokens of {}:{}",
        stored.id(),
        stored.accountType(),
        stored.accountId());
    refreshTokens.revokeAll(stored.accountType(), stored.accountId(), now);
  }

  private IssuedTokens issue(LoginAccount account, Instant now) {
    return issue(account, ids.newId(), now);
  }

  private IssuedTokens issue(LoginAccount account, UUID refreshTokenId, Instant now) {
    String refreshToken =
        refreshTokens.create(
            refreshTokenId,
            account.accountType(),
            account.id(),
            now.plus(properties.refreshTokenTtl()),
            now);
    return new IssuedTokens(
        accessTokens.issue(account, now), accessTokens.ttlSeconds(), refreshToken);
  }

  private static ApiException invalidCredentials() {
    return new ApiException(ErrorCode.INVALID_CREDENTIALS, "帳號或密碼錯誤");
  }

  private static ApiException locked(Instant lockedUntil) {
    return new ApiException(
        ErrorCode.ACCOUNT_LOCKED, "登入失敗次數過多，帳號暫時鎖定", Map.of("lockedUntil", lockedUntil.toString()));
  }

  private static ApiException invalidRefreshToken() {
    return new ApiException(ErrorCode.UNAUTHENTICATED, "更新權杖無效或已過期，請重新登入");
  }
}
