package com.marketplace.mockgateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketplace.mockgateway.testsupport.TestClockConfiguration;
import com.marketplace.mockgateway.testsupport.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** The mock gateway starts against a real database with all migrations applied. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestClockConfiguration.class})
class HealthApiIT {

  @Autowired MockMvcTester mvc;
  @Autowired JdbcClient jdbc;

  @Test
  @DisplayName("模擬金流啟動後回報健康")
  void reportsHealthy() {
    assertThat(mvc.get().uri("/actuator/health")).hasStatusOk();
  }

  @Test
  @DisplayName("Flyway 在 gateway schema 中管理資料表")
  void flywayManagesTheGatewaySchema() {
    Long count =
        jdbc.sql(
                """
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'gateway' AND table_name = 'flyway_schema_history'
                """)
            .query(Long.class)
            .single();

    assertThat(count).isOne();
  }
}
