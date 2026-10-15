package com.marketplace.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketplace.account.internal.service.InitialOperatorService;
import com.marketplace.testsupport.AccountFixtures;
import com.marketplace.testsupport.ApplicationTest;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The first operator administrator is created from the settings at startup (設計_005 3.4). */
@ApplicationTest
class InitialOperatorModuleTest {

  @Autowired InitialOperatorService initialOperator;
  @Autowired JdbcClient jdbc;

  @Test
  @DisplayName("沒有營運帳號時建立管理者：須修改密碼，並寫入稽核紀錄")
  void createdAtStartup() {
    Map<String, Object> operator =
        jdbc.sql(
                """
                SELECT u.id::text AS id, u.must_change_password, u.created_by, r.role
                FROM account.operator_user u
                JOIN account.operator_user_role r ON r.operator_user_id = u.id
                WHERE u.username = :username
                """)
            .param("username", AccountFixtures.INITIAL_OPERATOR_USERNAME)
            .query()
            .singleRow();

    assertThat(operator)
        .containsEntry("must_change_password", true)
        .containsEntry("created_by", "SYSTEM:建立第一個營運帳號")
        .containsEntry("role", "ADMIN");
    assertThat(
            jdbc.sql(
                    """
                    SELECT actor, action FROM shared.audit_log
                    WHERE target_type = 'OPERATOR' AND target_id = :id
                    """)
                .param("id", operator.get("id"))
                .query()
                .singleRow())
        .containsEntry("actor", "SYSTEM:建立第一個營運帳號")
        .containsEntry("action", "CREATE_OPERATOR_USER");
  }

  @Test
  @DisplayName("已有營運帳號時不再建立")
  void notCreatedAgain() {
    long before = operatorCount();

    initialOperator.run(new DefaultApplicationArguments());

    assertThat(operatorCount()).isEqualTo(before);
  }

  private long operatorCount() {
    return jdbc.sql("SELECT count(*) FROM account.operator_user").query(Long.class).single();
  }
}
