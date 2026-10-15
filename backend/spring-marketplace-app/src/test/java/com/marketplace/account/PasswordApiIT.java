package com.marketplace.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketplace.shared.AccountType;
import com.marketplace.testsupport.AccountFixtures;
import com.marketplace.testsupport.AccountFixtures.TestAccount;
import com.marketplace.testsupport.ApplicationTest;
import com.marketplace.testsupport.AuthTestClient;
import com.marketplace.testsupport.AuthTestClient.Tokens;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Changing the password and the initial-password rule (設計_005 2.7). */
@ApplicationTest
class PasswordApiIT {

  private static final String NEW_PASSWORD = "New-password-5678";

  @Autowired AccountFixtures accounts;
  @Autowired AuthTestClient auth;

  @ParameterizedTest
  @EnumSource(AccountType.class)
  @DisplayName("修改密碼：撤銷所有更新權杖，之後以新密碼登入")
  void changePassword(AccountType type) {
    TestAccount account = accounts.any(type);
    Tokens tokens = auth.loginOk(account);

    MvcTestResult result =
        auth.changePassword(type, tokens.accessToken(), account.password(), NEW_PASSWORD);

    assertThat(result).hasStatus(204);
    assertThat(auth.refresh(type, tokens.refreshToken())).hasStatus(401);
    assertThat(auth.login(type, account.username(), account.password())).hasStatus(401);
    assertThat(auth.login(type, account.username(), NEW_PASSWORD)).hasStatusOk();
  }

  @Test
  @DisplayName("目前的密碼錯誤：INVALID_CREDENTIALS，密碼不變")
  void wrongCurrentPassword() {
    TestAccount member = accounts.member();
    Tokens tokens = auth.loginOk(member);

    MvcTestResult result =
        auth.changePassword(AccountType.MEMBER, tokens.accessToken(), "wrong", NEW_PASSWORD);

    assertThat(result)
        .hasStatus(401)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("INVALID_CREDENTIALS");
    assertThat(auth.login(AccountType.MEMBER, member.username(), member.password())).hasStatusOk();
  }

  @Test
  @DisplayName("新密碼與目前的密碼相同：VALIDATION_FAILED")
  void newPasswordMustDiffer() {
    TestAccount member = accounts.member();
    Tokens tokens = auth.loginOk(member);

    MvcTestResult result =
        auth.changePassword(
            AccountType.MEMBER, tokens.accessToken(), member.password(), member.password());

    assertThat(result)
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.errors[0].field")
        .isEqualTo("newPassword");
  }

  @Test
  @DisplayName("新密碼少於 8 個字元：VALIDATION_FAILED")
  void newPasswordTooShort() {
    TestAccount member = accounts.member();
    Tokens tokens = auth.loginOk(member);

    MvcTestResult result =
        auth.changePassword(AccountType.MEMBER, tokens.accessToken(), member.password(), "short");

    assertThat(result)
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("VALIDATION_FAILED");
  }

  @Test
  @DisplayName("以初始密碼登入的帳號，修改密碼前呼叫其他 API 回應 PASSWORD_CHANGE_REQUIRED")
  void initialPasswordMustBeChanged() {
    TestAccount sellerUser = accounts.sellerUser("ADMIN", true);
    Tokens tokens = auth.loginOk(sellerUser);

    MvcTestResult blocked = auth.get("/api/v1/seller/products", tokens.accessToken());

    assertThat(blocked)
        .hasStatus(403)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("PASSWORD_CHANGE_REQUIRED");

    assertThat(
            auth.changePassword(
                AccountType.SELLER_USER, tokens.accessToken(), sellerUser.password(), NEW_PASSWORD))
        .hasStatus(204);
    Tokens afterChange =
        AuthTestClient.tokens(
            auth.login(AccountType.SELLER_USER, sellerUser.username(), NEW_PASSWORD));
    // The products API comes with the catalog module; until then the request reaches routing.
    assertThat(auth.get("/api/v1/seller/products", afterChange.accessToken())).hasStatus(404);
  }

  @Test
  @DisplayName("營運帳號同樣須先修改初始密碼")
  void operatorWithInitialPassword() {
    TestAccount operator = accounts.operator(Set.of("ADMIN"), true);
    Tokens tokens = auth.loginOk(operator);

    MvcTestResult blocked = auth.get("/api/v1/ops/sellers", tokens.accessToken());

    assertThat(blocked)
        .hasStatus(403)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("PASSWORD_CHANGE_REQUIRED");
  }

  @Test
  @DisplayName("啟動時建立的第一個營運管理者，以初始密碼登入後須先修改密碼")
  void initialOperator() {
    Tokens tokens =
        AuthTestClient.tokens(
            auth.login(
                AccountType.OPERATOR,
                AccountFixtures.INITIAL_OPERATOR_USERNAME,
                AccountFixtures.INITIAL_OPERATOR_PASSWORD));

    assertThat(auth.get("/api/v1/ops/sellers", tokens.accessToken()))
        .hasStatus(403)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("PASSWORD_CHANGE_REQUIRED");
  }
}
