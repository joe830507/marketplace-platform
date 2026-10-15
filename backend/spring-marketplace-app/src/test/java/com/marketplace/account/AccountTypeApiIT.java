package com.marketplace.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketplace.shared.AccountType;
import com.marketplace.testsupport.AccountFixtures;
import com.marketplace.testsupport.ApplicationTest;
import com.marketplace.testsupport.AuthTestClient;
import com.marketplace.testsupport.AuthTestClient.Tokens;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;

/** Each path prefix accepts only its own account type (設計_005 2.1). */
@ApplicationTest
class AccountTypeApiIT {

  @Autowired AccountFixtures accounts;
  @Autowired AuthTestClient auth;

  static Stream<Arguments> mismatchedPrefixes() {
    return Stream.of(AccountType.values())
        .flatMap(
            tokenType ->
                Stream.of(AccountType.values())
                    .filter(prefixType -> prefixType != tokenType)
                    .map(prefixType -> Arguments.of(tokenType, prefixType)));
  }

  @ParameterizedTest(name = "{0} 的權杖呼叫 {1} 的 API")
  @MethodSource("mismatchedPrefixes")
  void tokenOfAnotherAccountTypeIsForbidden(AccountType tokenType, AccountType prefixType) {
    Tokens tokens = auth.loginOk(accounts.any(tokenType));

    assertThat(auth.changePassword(prefixType, tokens.accessToken(), "anything", "New-password-1"))
        .hasStatus(403)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("FORBIDDEN");
  }
}
