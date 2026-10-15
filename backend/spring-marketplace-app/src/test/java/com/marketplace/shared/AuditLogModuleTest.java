package com.marketplace.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketplace.testsupport.ApplicationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/** Audit records are part of the change they describe (需求 3.6). */
@ApplicationTest
class AuditLogModuleTest {

  @Autowired AuditLog auditLog;
  @Autowired TransactionTemplate transactions;
  @Autowired JdbcClient jdbc;

  @Test
  @DisplayName("稽核紀錄與業務資料在同一交易：交易撤銷時，稽核紀錄也不存在")
  void rolledBackWithTheTransaction() {
    String targetId = UUID.randomUUID().toString();

    transactions.executeWithoutResult(
        status -> {
          auditLog.record(Actor.ANONYMOUS, "TEST", "TEST", targetId, null, Map.of("a", 1));
          status.setRollbackOnly();
        });

    assertThat(count(targetId)).isZero();
  }

  @Test
  @DisplayName("交易提交時寫入稽核紀錄，含操作者與變更後的值")
  void committedWithTheTransaction() {
    String targetId = UUID.randomUUID().toString();

    transactions.executeWithoutResult(
        status ->
            auditLog.record(
                Actor.system("測試"), "TEST", "TEST", targetId, null, Map.of("rate", 1000)));

    assertThat(count(targetId)).isEqualTo(1);
    assertThat(
            jdbc.sql("SELECT actor, after::text FROM shared.audit_log WHERE target_id = :id")
                .param("id", targetId)
                .query()
                .singleRow())
        .containsEntry("actor", "SYSTEM:測試")
        .containsEntry("after", "{\"rate\": 1000}");
  }

  @Test
  @DisplayName("沒有交易時拒絕寫入，避免稽核紀錄與資料異動分開提交")
  void requiresATransaction() {
    assertThatThrownBy(() -> auditLog.record(Actor.ANONYMOUS, "TEST", "TEST", "x", null, null))
        .isInstanceOf(IllegalTransactionStateException.class);
  }

  private long count(String targetId) {
    return jdbc.sql("SELECT count(*) FROM shared.audit_log WHERE target_id = :id")
        .param("id", targetId)
        .query(Long.class)
        .single();
  }
}
