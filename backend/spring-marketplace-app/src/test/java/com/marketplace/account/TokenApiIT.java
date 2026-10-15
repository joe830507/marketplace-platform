package com.marketplace.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketplace.shared.AccountType;
import com.marketplace.testsupport.AccountFixtures;
import com.marketplace.testsupport.AccountFixtures.TestAccount;
import com.marketplace.testsupport.ApplicationTest;
import com.marketplace.testsupport.AuthTestClient;
import com.marketplace.testsupport.AuthTestClient.Tokens;
import com.marketplace.testsupport.MutableClock;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Access and refresh tokens (ADR_009, 設計_004 3, 設計_005 2.7). */
@ApplicationTest
class TokenApiIT {

  @Autowired AccountFixtures accounts;
  @Autowired AuthTestClient auth;
  @Autowired MutableClock clock;

  @Test
  @DisplayName("存取權杖 15 分鐘後失效")
  void accessTokenExpiresAfterFifteenMinutes() {
    TestAccount member = accounts.member();
    Tokens tokens = auth.loginOk(member);

    clock.advance(Duration.ofMinutes(14).plusSeconds(59));
    assertThat(auth.isAccepted(AccountType.MEMBER, tokens.accessToken())).isTrue();

    clock.advance(Duration.ofSeconds(1));
    assertThat(auth.isAccepted(AccountType.MEMBER, tokens.accessToken())).isFalse();
  }

  @ParameterizedTest
  @EnumSource(AccountType.class)
  @DisplayName("換發：回應新的存取權杖與更新權杖，舊的更新權杖立即失效")
  void refreshReplacesTheRefreshToken(AccountType type) {
    Tokens first = auth.loginOk(accounts.any(type));

    MvcTestResult result = auth.refresh(type, first.refreshToken());

    Tokens second = AuthTestClient.tokens(result);
    assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
    assertThat(auth.isAccepted(type, second.accessToken())).isTrue();
    assertThat(auth.refresh(type, second.refreshToken())).hasStatusOk();
  }

  @Test
  @DisplayName("已換發過的更新權杖再次被使用時，撤銷該帳號所有更新權杖")
  void reusingAnExchangedTokenRevokesAll() {
    TestAccount member = accounts.member();
    Tokens first = auth.loginOk(member);
    Tokens otherDevice = auth.loginOk(member);
    Tokens second = AuthTestClient.tokens(auth.refresh(AccountType.MEMBER, first.refreshToken()));

    MvcTestResult reuse = auth.refresh(AccountType.MEMBER, first.refreshToken());

    assertThat(reuse)
        .hasStatus(401)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("UNAUTHENTICATED");
    assertThat(auth.refresh(AccountType.MEMBER, second.refreshToken())).hasStatus(401);
    assertThat(auth.refresh(AccountType.MEMBER, otherDevice.refreshToken())).hasStatus(401);
  }

  @Test
  @DisplayName("更新權杖 7 天後失效")
  void refreshTokenExpiresAfterSevenDays() {
    Tokens tokens = auth.loginOk(accounts.member());

    clock.advance(Duration.ofDays(7));

    assertThat(auth.refresh(AccountType.MEMBER, tokens.refreshToken())).hasStatus(401);
  }

  @Test
  @DisplayName("沒有或不認得的更新權杖：UNAUTHENTICATED")
  void missingOrUnknownRefreshToken() {
    assertThat(auth.refresh(AccountType.MEMBER, null))
        .hasStatus(401)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("UNAUTHENTICATED");
    assertThat(auth.refresh(AccountType.MEMBER, "not-a-token")).hasStatus(401);
  }

  @Test
  @DisplayName("更新權杖只能在自己類型的前綴換發")
  void refreshTokenIsBoundToItsAccountType() {
    Tokens tokens = auth.loginOk(accounts.member());

    assertThat(auth.refresh(AccountType.SELLER_USER, tokens.refreshToken())).hasStatus(401);
    assertThat(auth.refresh(AccountType.MEMBER, tokens.refreshToken())).hasStatusOk();
  }

  @Test
  @DisplayName("停用的帳號無法換發")
  void disabledAccountCannotRefresh() {
    TestAccount member = accounts.member();
    Tokens tokens = auth.loginOk(member);
    accounts.disable(member);

    assertThat(auth.refresh(AccountType.MEMBER, tokens.refreshToken())).hasStatus(401);
  }

  @ParameterizedTest
  @EnumSource(AccountType.class)
  @DisplayName("登出：撤銷該帳號所有更新權杖並清除 Cookie")
  void logoutRevokesAllRefreshTokens(AccountType type) {
    TestAccount account = accounts.any(type);
    Tokens tokens = auth.loginOk(account);
    Tokens otherDevice = auth.loginOk(account);

    MvcTestResult result = auth.logout(type, tokens.refreshToken());

    assertThat(result).hasStatus(204);
    assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
        .startsWith(AuthTestClient.REFRESH_TOKEN_COOKIE + "=;")
        .contains("; Max-Age=0")
        .contains("; Path=" + type.apiPrefix() + "/auth");
    assertThat(auth.refresh(type, tokens.refreshToken())).hasStatus(401);
    assertThat(auth.refresh(type, otherDevice.refreshToken())).hasStatus(401);
  }

  @Test
  @DisplayName("以無效的更新權杖登出仍回應 204 並清除 Cookie")
  void logoutWithAnInvalidToken() {
    assertThat(auth.logout(AccountType.MEMBER, "not-a-token")).hasStatus(204);
  }
}
