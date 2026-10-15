package com.marketplace.account.internal.entity;

import com.marketplace.account.internal.repository.LoginStateStore;
import com.marketplace.shared.AccountType;
import com.marketplace.shared.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * What the three kinds of accounts have in common for logging in.
 *
 * <p>Login state ({@code failedLoginCount}, {@code lockedUntil}) is changed only through {@link
 * LoginStateStore}, with atomic SQL, so concurrent failed logins cannot skip the lock.
 */
@MappedSuperclass
public abstract class LoginAccount extends AuditableEntity {

  @Id private UUID id;

  @Column(nullable = false)
  private String username;

  @Column(nullable = false)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AccountStatus status;

  @Column(nullable = false, insertable = false, updatable = false)
  private int failedLoginCount;

  @Column(insertable = false, updatable = false)
  private @Nullable Instant lockedUntil;

  protected LoginAccount() {}

  /** A new active account that has never failed a login. */
  protected LoginAccount(UUID id, String username, String passwordHash) {
    this.id = id;
    this.username = username;
    this.passwordHash = passwordHash;
    this.status = AccountStatus.ACTIVE;
  }

  public abstract AccountType accountType();

  /** Role names as they appear in the access token; empty for members. */
  public abstract Set<String> roles();

  public @Nullable UUID sellerId() {
    return null;
  }

  public boolean mustChangePassword() {
    return false;
  }

  /** Called after the password is changed; accounts with an initial password clear the flag. */
  public void changePassword(String newPasswordHash) {
    this.passwordHash = newPasswordHash;
  }

  public UUID id() {
    return id;
  }

  public String username() {
    return username;
  }

  public String passwordHash() {
    return passwordHash;
  }

  public AccountStatus status() {
    return status;
  }

  public boolean isActive() {
    return status == AccountStatus.ACTIVE;
  }

  public int failedLoginCount() {
    return failedLoginCount;
  }

  public @Nullable Instant lockedUntil() {
    return lockedUntil;
  }
}
