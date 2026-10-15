package com.marketplace.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.marketplace.shared.AccountType;
import com.marketplace.testsupport.AccountFixtures.TestAccount;
import jakarta.servlet.http.Cookie;
import java.io.UnsupportedEncodingException;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Calls the auth API of each account type (設計_005 2.7). */
@TestComponent
public class AuthTestClient {

  public static final String REFRESH_TOKEN_COOKIE = "refresh_token";

  /** The tokens a successful login or refresh returns. */
  public record Tokens(String accessToken, String refreshToken) {}

  private final MockMvcTester mvc;

  AuthTestClient(MockMvcTester mvc) {
    this.mvc = mvc;
  }

  public MvcTestResult login(AccountType type, String username, String password) {
    return mvc.post()
        .uri(type.apiPrefix() + "/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            """
            {"username": "%s", "password": "%s"}
            """
                .formatted(username, password))
        .exchange();
  }

  public Tokens loginOk(TestAccount account) {
    return tokens(login(account.type(), account.username(), account.password()));
  }

  public MvcTestResult refresh(AccountType type, @Nullable String refreshToken) {
    var request = mvc.post().uri(type.apiPrefix() + "/auth/refresh");
    if (refreshToken != null) {
      request = request.cookie(new Cookie(REFRESH_TOKEN_COOKIE, refreshToken));
    }
    return request.exchange();
  }

  public MvcTestResult logout(AccountType type, String refreshToken) {
    return mvc.post()
        .uri(type.apiPrefix() + "/auth/logout")
        .cookie(new Cookie(REFRESH_TOKEN_COOKIE, refreshToken))
        .exchange();
  }

  public MvcTestResult changePassword(
      AccountType type, String accessToken, String currentPassword, String newPassword) {
    return mvc.post()
        .uri(type.apiPrefix() + "/auth/password")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            """
            {"currentPassword": "%s", "newPassword": "%s"}
            """
                .formatted(currentPassword, newPassword))
        .exchange();
  }

  public MvcTestResult get(String path, String accessToken) {
    return mvc.get()
        .uri(path)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .exchange();
  }

  /**
   * Checks whether an access token is accepted, using the only protected API that exists for every
   * account type: changing the password with a wrong current password answers {@code
   * INVALID_CREDENTIALS} when the token is accepted and {@code UNAUTHENTICATED} when it is not.
   */
  public boolean isAccepted(AccountType type, String accessToken) {
    String code =
        JsonPath.read(
            body(changePassword(type, accessToken, "wrong-current-password", "New-password-1")),
            "$.code");
    assertThat(code).isIn("INVALID_CREDENTIALS", "UNAUTHENTICATED");
    return code.equals("INVALID_CREDENTIALS");
  }

  public static Tokens tokens(MvcTestResult result) {
    assertThat(result).hasStatusOk();
    Cookie cookie = result.getResponse().getCookie(REFRESH_TOKEN_COOKIE);
    assertThat(cookie).isNotNull();
    return new Tokens(JsonPath.read(body(result), "$.accessToken"), cookie.getValue());
  }

  public static String body(MvcTestResult result) {
    try {
      return result.getResponse().getContentAsString();
    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException(e);
    }
  }
}
