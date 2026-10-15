package com.marketplace.account.internal.service;

import com.marketplace.account.OperatorRole;
import com.marketplace.account.internal.entity.OperatorUser;
import com.marketplace.account.internal.repository.OperatorUserRepository;
import com.marketplace.account.internal.repository.RefreshTokenStore;
import com.marketplace.shared.AccountType;
import com.marketplace.shared.Actor;
import com.marketplace.shared.ApiException;
import com.marketplace.shared.AuditLog;
import com.marketplace.shared.ErrorCode;
import com.marketplace.shared.IdGenerator;
import com.marketplace.shared.PageResult;
import com.marketplace.shared.Paging;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operator accounts and their roles, managed by operator administrators (需求 3.13).
 *
 * <p>Role changes are level-two audit records (需求 3.6). They take effect when the account's access
 * token expires, at most 15 minutes later; its refresh tokens are revoked at once so that it cannot
 * get a token with the old roles (ADR_009).
 */
@Service
public class OperatorUserService {

  /** Newest first; identifiers are UUIDv7, so they order accounts created in the same instant. */
  private static final Sort NEWEST_FIRST =
      Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

  public record OperatorView(UUID id, String username, boolean active, Set<OperatorRole> roles) {}

  private final OperatorUserRepository operators;
  private final RefreshTokenStore refreshTokens;
  private final PasswordEncoder passwordEncoder;
  private final AuditLog auditLog;
  private final IdGenerator ids;
  private final Clock clock;

  OperatorUserService(
      OperatorUserRepository operators,
      RefreshTokenStore refreshTokens,
      PasswordEncoder passwordEncoder,
      AuditLog auditLog,
      IdGenerator ids,
      Clock clock) {
    this.operators = operators;
    this.refreshTokens = refreshTokens;
    this.passwordEncoder = passwordEncoder;
    this.auditLog = auditLog;
    this.ids = ids;
    this.clock = clock;
  }

  /** Operator accounts, newest first. */
  @Transactional(readOnly = true)
  public PageResult<OperatorView> list(Paging paging) {
    Page<OperatorUser> page =
        operators.findAll(PageRequest.of(paging.page() - 1, paging.size(), NEWEST_FIRST));
    return PageResult.of(page.getContent(), paging, page.getTotalElements())
        .map(OperatorUserService::view);
  }

  /** Creates an account with an initial password that must be changed at first login. */
  @Transactional
  public OperatorView create(String username, String initialPassword, Set<OperatorRole> roles) {
    if (operators.findByUsername(username).isPresent()) {
      throw MemberService.usernameTaken();
    }
    Actor actor = Actor.current();
    OperatorUser operator =
        new OperatorUser(
            ids.newId(),
            username,
            passwordEncoder.encode(initialPassword),
            roles,
            clock.instant(),
            actor);
    try {
      operators.saveAndFlush(operator);
    } catch (DataIntegrityViolationException e) {
      throw MemberService.usernameTaken();
    }
    auditLog.record(
        actor,
        "CREATE_OPERATOR_USER",
        AccountType.OPERATOR.name(),
        operator.id().toString(),
        null,
        Map.of("username", username, "roles", sorted(roles)));
    return view(operator);
  }

  /** Replaces the account's roles. */
  @Transactional
  public OperatorView setRoles(UUID operatorUserId, Set<OperatorRole> roles) {
    OperatorUser operator =
        operators
            .findById(operatorUserId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "營運帳號不存在"));
    Set<OperatorRole> before = operator.roleSet();
    if (before.equals(roles)) {
      return view(operator);
    }
    Actor actor = Actor.current();
    Instant now = clock.instant();
    operator.assignRoles(roles, now, actor);
    refreshTokens.revokeAll(AccountType.OPERATOR, operatorUserId, now);
    auditLog.record(
        actor,
        "SET_OPERATOR_ROLES",
        AccountType.OPERATOR.name(),
        operatorUserId.toString(),
        Map.of("roles", sorted(before)),
        Map.of("roles", sorted(roles)));
    return view(operator);
  }

  private static OperatorView view(OperatorUser operator) {
    return new OperatorView(
        operator.id(), operator.username(), operator.isActive(), operator.roleSet());
  }

  private static List<String> sorted(Set<OperatorRole> roles) {
    return roles.stream().sorted().map(OperatorRole::name).toList();
  }
}
