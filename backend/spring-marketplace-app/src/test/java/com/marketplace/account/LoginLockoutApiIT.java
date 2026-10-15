package com.marketplace.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import com.jayway.jsonpath.JsonPath;
import com.marketplace.shared.AccountType;
import com.marketplace.testsupport.AccountFixtures;
import com.marketplace.testsupport.AccountFixtures.TestAccount;
import com.marketplace.testsupport.ApplicationTest;
import com.marketplace.testsupport.AuthTestClient;
import com.marketplace.testsupport.AuthTestClient.Tokens;
import com.marketplace.testsupport.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** A1 登入失敗鎖定 (需求_002 A1, 需求 3.13). */
@ApplicationTest
class LoginLockoutApiIT {

  private static final String WRONG_PASSWORD = "wrong-password";

  @Autowired AccountFixtures accounts;
  @Autowired AuthTestClient auth;
  @Autowired MutableClock clock;
  @Autowired JdbcClient jdbc;

  @Test
  @DisplayName("A1 連續失敗：以錯誤密碼連續登入 5 次，帳號鎖定 15 分鐘，並留下稽核紀錄")
  void fiveFailuresLockTheAccount() {
    TestAccount member = accounts.member();
    for (int i = 0; i < 4; i++) {
      assertThat(failLogin(member))
          .bodyJson()
          .extractingPath("$.code")
          .isEqualTo("INVALID_CREDENTIALS");
    }

    MvcTestResult fifth = failLogin(member);

    Instant expectedLock = clock.instant().plus(Duration.ofMinutes(15));
    assertThat(fifth)
        .hasStatus(401)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("ACCOUNT_LOCKED");
    assertThat(fifth).bodyJson().extractingPath("$.lockedUntil").isEqualTo(expectedLock.toString());

    List<Map<String, Object>> audit = auditRecords(member);
    assertThat(audit).hasSize(1);
    assertThat(audit.getFirst())
        .containsEntry("actor", "ANONYMOUS")
        .containsEntry("action", "LOCK_ACCOUNT")
        .containsEntry("target_type", "MEMBER")
        .containsEntry("trace_id", JsonPath.read(AuthTestClient.body(fifth), "$.traceId"));
    assertThat((String) audit.getFirst().get("after"))
        .contains("\"failedLoginCount\": 5")
        .contains(expectedLock.toString());
  }

  @Test
  @DisplayName("A1 鎖定期間：以正確密碼登入仍失敗，回應提示帳號暫時鎖定")
  void correctPasswordIsRejectedWhileLocked() {
    TestAccount member = accounts.member();
    lock(member);
    clock.advance(Duration.ofMinutes(14).plusSeconds(59));

    MvcTestResult result = auth.login(AccountType.MEMBER, member.username(), member.password());

    assertThat(result)
        .hasStatus(401)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("ACCOUNT_LOCKED");
  }

  @Test
  @DisplayName("A1 鎖定結束：15 分鐘後以正確密碼登入成功，並留下解鎖的稽核紀錄")
  void lockExpiresAfterFifteenMinutes() {
    TestAccount member = accounts.member();
    lock(member);
    clock.advance(Duration.ofMinutes(15));

    MvcTestResult result = auth.login(AccountType.MEMBER, member.username(), member.password());

    assertThat(result).hasStatusOk();
    assertThat(auditRecords(member))
        .extracting(row -> row.get("action"), row -> row.get("actor"))
        .containsExactly(
            tuple("LOCK_ACCOUNT", "ANONYMOUS"), tuple("UNLOCK_ACCOUNT", "SYSTEM:鎖定期滿"));
    assertThat(failedLoginCount(member)).isZero();
  }

  @Test
  @DisplayName("A1 鎖定結束後重新計算：鎖定期滿後再失敗 1 次不會立即鎖定")
  void countStartsOverAfterTheLockExpires() {
    TestAccount member = accounts.member();
    lock(member);
    clock.advance(Duration.ofMinutes(15));

    MvcTestResult result = failLogin(member);

    assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_CREDENTIALS");
    assertThat(failedLoginCount(member)).isEqualTo(1);
  }

  @Test
  @DisplayName("A1 失敗後成功：連續失敗 4 次後以正確密碼登入成功，失敗次數歸零")
  void successResetsTheCount() {
    TestAccount member = accounts.member();
    for (int i = 0; i < 4; i++) {
      failLogin(member);
    }

    MvcTestResult result = auth.login(AccountType.MEMBER, member.username(), member.password());

    assertThat(result).hasStatusOk();
    assertThat(failedLoginCount(member)).isZero();
    assertThat(auditRecords(member)).isEmpty();
  }

  @Test
  @DisplayName("帳號鎖定時撤銷該帳號所有的更新權杖（ADR_009）")
  void lockRevokesRefreshTokens() {
    TestAccount member = accounts.member();
    Tokens tokens = auth.loginOk(member);

    lock(member);

    assertThat(auth.refresh(AccountType.MEMBER, tokens.refreshToken()))
        .hasStatus(401)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("UNAUTHENTICATED");
  }

  @Test
  @DisplayName("賣家帳號與營運帳號同樣會鎖定")
  void sellerAndOperatorAccountsAreLockedToo() {
    for (TestAccount account :
        List.of(accounts.sellerUser("MEMBER", false), accounts.operator(Set.of(), false))) {
      lock(account);

      MvcTestResult result = auth.login(account.type(), account.username(), account.password());

      assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("ACCOUNT_LOCKED");
    }
  }

  private void lock(TestAccount account) {
    for (int i = 0; i < 5; i++) {
      failLogin(account);
    }
  }

  private MvcTestResult failLogin(TestAccount account) {
    return auth.login(account.type(), account.username(), WRONG_PASSWORD);
  }

  private List<Map<String, Object>> auditRecords(TestAccount account) {
    return jdbc.sql(
            """
            SELECT actor, action, target_type, trace_id, before::text AS before, after::text AS after
            FROM shared.audit_log
            WHERE target_id = :id
            ORDER BY created_at, action
            """)
        .param("id", account.id().toString())
        .query()
        .listOfRows();
  }

  private int failedLoginCount(TestAccount account) {
    return jdbc.sql("SELECT failed_login_count FROM account.member WHERE id = :id")
        .param("id", account.id())
        .query(Integer.class)
        .single();
  }
}
