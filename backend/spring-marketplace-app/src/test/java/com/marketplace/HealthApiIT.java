package com.marketplace;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketplace.testsupport.TestClockConfiguration;
import com.marketplace.testsupport.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** The application starts against a real database with all migrations applied. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestClockConfiguration.class})
class HealthApiIT {

  @Autowired MockMvcTester mvc;
  @Autowired JdbcClient jdbc;

  @Test
  @DisplayName("應用程式啟動後回報健康")
  void reportsHealthy() {
    assertThat(mvc.get().uri("/actuator/health")).hasStatusOk();
  }

  @Test
  @DisplayName("資料庫遷移已建立事件持久化的資料表")
  void migrationsCreateTheEventPublicationTable() {
    Long count =
        jdbc.sql("SELECT count(*) FROM shared.event_publication").query(Long.class).single();

    assertThat(count).isZero();
  }
}
