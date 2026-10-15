package com.marketplace.account.internal.service;

import com.marketplace.account.internal.config.AuthProperties;
import com.marketplace.account.internal.config.AuthProperties.InitialOperator;
import com.marketplace.account.internal.repository.InitialOperatorStore;
import com.marketplace.shared.AccountType;
import com.marketplace.shared.Actor;
import com.marketplace.shared.AuditLog;
import com.marketplace.shared.IdGenerator;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first operator administrator at startup while no operator exists (設計_005 3.4). Every
 * other account is created through the API.
 *
 * <p>The initial password is given at deployment, in {@code
 * marketplace.auth.initial-operator.password}, so it is never in the repository. Once any operator
 * exists the setting is ignored.
 */
@Service
public class InitialOperatorService implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(InitialOperatorService.class);

  static final Actor ACTOR = Actor.system("建立第一個營運帳號");

  /** The same limits as a new password in the API. */
  private static final int MIN_PASSWORD_LENGTH = 8;

  private static final int MAX_PASSWORD_LENGTH = 100;

  private final InitialOperatorStore store;
  private final AuthProperties properties;
  private final PasswordEncoder passwordEncoder;
  private final AuditLog auditLog;
  private final IdGenerator ids;
  private final Clock clock;

  InitialOperatorService(
      InitialOperatorStore store,
      AuthProperties properties,
      PasswordEncoder passwordEncoder,
      AuditLog auditLog,
      IdGenerator ids,
      Clock clock) {
    this.store = store;
    this.properties = properties;
    this.passwordEncoder = passwordEncoder;
    this.auditLog = auditLog;
    this.ids = ids;
    this.clock = clock;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (store.anyOperatorExists()) {
      return;
    }
    InitialOperator settings = properties.initialOperator();
    String password = settings.password();
    if (password == null || password.isEmpty()) {
      log.warn(
          "No operator account exists. Set marketplace.auth.initial-operator.password"
              + " (MARKETPLACE_AUTH_INITIAL_OPERATOR_PASSWORD) and restart to create {}.",
          settings.username());
      return;
    }
    if (password.length() < MIN_PASSWORD_LENGTH || password.length() > MAX_PASSWORD_LENGTH) {
      throw new IllegalStateException(
          "marketplace.auth.initial-operator.password must be %d to %d characters"
              .formatted(MIN_PASSWORD_LENGTH, MAX_PASSWORD_LENGTH));
    }

    UUID id = ids.newId();
    boolean created =
        store.insertAdministrator(
            id, settings.username(), passwordEncoder.encode(password), clock.instant(), ACTOR);
    if (!created) {
      return;
    }
    auditLog.record(
        ACTOR,
        "CREATE_OPERATOR_USER",
        AccountType.OPERATOR.name(),
        id.toString(),
        null,
        Map.of("username", settings.username(), "roles", List.of("ADMIN")));
    log.info(
        "Created the first operator account {}; it must change its password", settings.username());
  }
}
