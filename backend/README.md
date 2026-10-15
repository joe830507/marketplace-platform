# 後端

| 專案 | 說明 | 連接埠 |
|---|---|---|
| `spring-marketplace-app` | 主應用程式，模組化單體 | 8080 |
| `spring-mock-payment-gateway` | 模擬金流 | 8081 |

兩個專案各自以 `spring-boot-starter-parent` 為上層，可以單獨建置；本資料夾的 `pom.xml` 只負責一次建置兩者。

## 環境需求

- JDK 25，安裝在 `D:\Programming\java\jdk\`（ADR_002）
- Docker：測試以 Testcontainers 啟動 PostgreSQL，本機執行以 Docker Compose 提供資料庫
- 不需要另外安裝 Maven，使用 Maven Wrapper

系統的 `JAVA_HOME` 維持 JDK 21 也可以。Maven 以 JDK 21 執行，編譯、測試與 `spring-boot:run` 透過 Maven Toolchains 使用 JDK 25，需要在 `~/.m2/toolchains.xml` 登記：

```xml
<toolchains>
  <toolchain>
    <type>jdk</type>
    <provides>
      <version>25</version>
    </provides>
    <configuration>
      <jdkHome>D:/Programming/java/jdk/jdk-25.0.4.1+1</jdkHome>
    </configuration>
  </toolchain>
</toolchains>
```

## 指令

在本資料夾執行：

| 指令 | 內容 |
|---|---|
| `./mvnw test` | 單元、模組、架構測試 |
| `./mvnw verify` | 上述測試，加上 API、併發測試、程式格式檢查與覆蓋率報告；提交程式之前執行 |
| `./mvnw verify -Pe2e` | 上述全部，加上端對端測試 |
| `./mvnw spotless:apply` | 依 Google Java Style 格式化程式 |

只建置其中一個專案時加上 `-f`，例如 `./mvnw -f spring-marketplace-app/pom.xml verify`。

測試類別以後綴區分層級，Surefire 與 Failsafe 依後綴分工，見《設計_006_測試計畫》4.3。覆蓋率報告在各專案的 `target/site/jacoco/` 與 `target/site/jacoco-it/`。

## 本機執行

```sh
docker compose -f ../infra/docker-compose.yml up -d --wait
./mvnw -f spring-marketplace-app/pom.xml spring-boot:run
./mvnw -f spring-mock-payment-gateway/pom.xml spring-boot:run
```

健康檢查：`http://localhost:8080/actuator/health`、`http://localhost:8081/actuator/health`。

資料庫只在第一次建立時執行 `infra/postgres/init/` 的腳本。要重建本機資料庫時，以 `docker compose -f ../infra/docker-compose.yml down -v` 刪除資料後再啟動。

## OpenAPI 規格

規格檔在各專案的 `src/main/resources/openapi/`。建置時產生 Java 介面與資料模型到 `target/generated-sources/openapi/`，控制器實作這些介面（ADR_011）。修改 API 時先改規格檔。
