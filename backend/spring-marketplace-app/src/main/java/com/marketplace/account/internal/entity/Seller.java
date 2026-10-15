package com.marketplace.account.internal.entity;

import com.marketplace.account.SellerType;
import com.marketplace.shared.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** A seller is an organization; people work for it through seller accounts (需求 3.2). */
@Entity
@Table(schema = "account", name = "seller")
public class Seller extends AuditableEntity {

  @Id private UUID id;

  @Column(nullable = false)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "seller_type", nullable = false)
  private SellerType type;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AccountStatus status;

  protected Seller() {}

  public Seller(UUID id, String name, SellerType type) {
    this.id = id;
    this.name = name;
    this.type = type;
    this.status = AccountStatus.ACTIVE;
  }

  public UUID id() {
    return id;
  }

  public String name() {
    return name;
  }

  public SellerType type() {
    return type;
  }

  public AccountStatus status() {
    return status;
  }

  public boolean isActive() {
    return status == AccountStatus.ACTIVE;
  }
}
