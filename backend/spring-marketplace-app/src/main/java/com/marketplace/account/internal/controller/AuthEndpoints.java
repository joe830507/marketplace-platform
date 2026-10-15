package com.marketplace.account.internal.controller;

import com.marketplace.account.internal.config.AuthProperties;
import com.marketplace.account.internal.service.AuthService;
import com.marketplace.account.internal.service.AuthService.IssuedTokens;
import com.marketplace.openapi.model.AccessToken;
import com.marketplace.openapi.model.ChangePasswordRequest;
import com.marketplace.openapi.model.LoginRequest;
import com.marketplace.shared.AccountType;
import com.marketplace.shared.AuthenticatedAccount;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

/**
 * The HTTP side of the auth API, shared by the member, seller and operator controllers.
 *
 * <p>The refresh token travels only in an HttpOnly, Secure, SameSite=Strict cookie limited to the
 * prefix's {@code /auth} path (ADR_011, 設計_005 2.7).
 */
@Component
public class AuthEndpoints {

  static final String REFRESH_TOKEN_COOKIE = "refresh_token";

  private final AuthService authService;
  private final AuthProperties properties;
  private final HttpServletRequest request;

  AuthEndpoints(AuthService authService, AuthProperties properties, HttpServletRequest request) {
    this.authService = authService;
    this.properties = properties;
    this.request = request;
  }

  ResponseEntity<AccessToken> login(AccountType type, LoginRequest body) {
    return issued(type, authService.login(type, body.getUsername(), body.getPassword()));
  }

  ResponseEntity<AccessToken> refresh(AccountType type) {
    return issued(type, authService.refresh(type, refreshToken()));
  }

  ResponseEntity<Void> logout(AccountType type) {
    authService.logout(type, refreshToken());
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, cookie(type, "", Duration.ZERO))
        .build();
  }

  ResponseEntity<Void> changePassword(AccountType type, ChangePasswordRequest body) {
    authService.changePassword(
        AuthenticatedAccount.require(), body.getCurrentPassword(), body.getNewPassword());
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, cookie(type, "", Duration.ZERO))
        .build();
  }

  private ResponseEntity<AccessToken> issued(AccountType type, IssuedTokens tokens) {
    AccessToken body =
        new AccessToken(
            tokens.accessToken(),
            AccessToken.TokenTypeEnum.BEARER,
            Math.toIntExact(tokens.expiresInSeconds()));
    return ResponseEntity.ok()
        .header(
            HttpHeaders.SET_COOKIE,
            cookie(type, tokens.refreshToken(), properties.refreshTokenTtl()))
        .body(body);
  }

  private @Nullable String refreshToken() {
    Cookie cookie = WebUtils.getCookie(request, REFRESH_TOKEN_COOKIE);
    return cookie == null || cookie.getValue().isEmpty() ? null : cookie.getValue();
  }

  /** A zero max age tells the browser to delete the cookie. */
  private static String cookie(AccountType type, String value, Duration maxAge) {
    return ResponseCookie.from(REFRESH_TOKEN_COOKIE, value)
        .httpOnly(true)
        .secure(true)
        .sameSite("Strict")
        .path(type.apiPrefix() + "/auth")
        .maxAge(maxAge)
        .build()
        .toString();
  }
}
