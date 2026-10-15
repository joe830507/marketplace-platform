package com.marketplace.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketplace.shared.AccountType;
import com.marketplace.testsupport.AccountFixtures;
import com.marketplace.testsupport.AccountFixtures.TestAccount;
import com.marketplace.testsupport.ApplicationTest;
import com.marketplace.testsupport.AuthTestClient;
import com.marketplace.testsupport.AuthTestClient.Tokens;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Login of the three account types (需求 3.13, 設計_005 2.7). */
@ApplicationTest
class LoginApiIT {

  @Autowired AccountFixtures accounts;
  @Autowired AuthTestClient auth;

  @ParameterizedTest
  @EnumSource(AccountType.class)
  @DisplayName("登入成功：回應存取權杖，可用來呼叫需要身分的 API")
  void loginIssuesAnAccessToken(AccountType type) {
    TestAccount account = accounts.any(type);

    MvcTestResult result = auth.login(type, account.username(), account.password());

    assertThat(result)
        .hasStatusOk()
        .bodyJson()
        .hasPathSatisfying("$.tokenType", value -> assertThat(value).isEqualTo("Bearer"))
        .hasPathSatisfying("$.expiresIn", value -> assertThat(value).isEqualTo(900));
    Tokens tokens = AuthTestClient.tokens(result);
    assertThat(auth.isAccepted(type, tokens.accessToken())).isTrue();
  }

  @ParameterizedTest
  @EnumSource(AccountType.class)
  @DisplayName("更新權杖的 Cookie 為 HttpOnly、Secure、SameSite=Strict，Path 限定在對應前綴的 /auth")
  void refreshTokenCookieIsRestricted(AccountType type) {
    TestAccount account = accounts.any(type);

    MvcTestResult result = auth.login(type, account.username(), account.password());

    assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
        .startsWith(AuthTestClient.REFRESH_TOKEN_COOKIE + "=")
        .contains("; Path=" + type.apiPrefix() + "/auth")
        .contains("; Max-Age=604800")
        .contains("; Secure")
        .contains("; HttpOnly")
        .contains("; SameSite=Strict");
  }

  @Test
  @DisplayName("帳號不分大小寫")
  void usernameIgnoresCase() {
    TestAccount member = accounts.member();

    MvcTestResult result =
        auth.login(
            AccountType.MEMBER, member.username().toUpperCase(Locale.ROOT), member.password());

    assertThat(result).hasStatusOk();
  }

  @Test
  @DisplayName("密碼錯誤：INVALID_CREDENTIALS")
  void wrongPassword() {
    TestAccount member = accounts.member();

    MvcTestResult result = auth.login(AccountType.MEMBER, member.username(), "wrong-password");

    assertThat(result)
        .hasStatus(401)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("INVALID_CREDENTIALS");
    assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
  }

  @Test
  @DisplayName("帳號不存在：與密碼錯誤的回應相同，不透露帳號是否存在")
  void unknownUsername() {
    MvcTestResult result = auth.login(AccountType.MEMBER, "no-such-member", "whatever");

    assertThat(result)
        .hasStatus(401)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("INVALID_CREDENTIALS");
  }

  @Test
  @DisplayName("帳號只能從自己類型的前綴登入：會員帳號無法從賣家的登入 API 登入")
  void accountTypesAreSeparate() {
    TestAccount member = accounts.member();

    MvcTestResult result =
        auth.login(AccountType.SELLER_USER, member.username(), member.password());

    assertThat(result)
        .hasStatus(401)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("INVALID_CREDENTIALS");
  }

  @Test
  @DisplayName("停用的帳號無法登入")
  void disabledAccount() {
    TestAccount member = accounts.member();
    accounts.disable(member);

    MvcTestResult result = auth.login(AccountType.MEMBER, member.username(), member.password());

    assertThat(result)
        .hasStatus(401)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("INVALID_CREDENTIALS");
  }

  @Test
  @DisplayName("賣家停用後，其賣家帳號無法登入")
  void accountOfDisabledSeller() {
    TestAccount sellerUser = accounts.sellerUser("ADMIN", false);
    accounts.disableSeller(sellerUser.sellerId());

    MvcTestResult result =
        auth.login(AccountType.SELLER_USER, sellerUser.username(), sellerUser.password());

    assertThat(result)
        .hasStatus(401)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("INVALID_CREDENTIALS");
  }
}
