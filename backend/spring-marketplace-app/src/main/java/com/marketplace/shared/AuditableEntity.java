package com.marketplace.shared;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.time.Instant;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * The level-three audit columns every mutable table has (設計_004 2.3), filled in by Spring Data
 * auditing from the injected clock and the current {@link Actor}.
 *
 * <p>Code that updates these tables with SQL instead of JPA sets the same columns itself and
 * increments {@code version}.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity {

  @CreatedDate
  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @CreatedBy
  @Column(nullable = false, updatable = false)
  private String createdBy;

  @LastModifiedDate
  @Column(nullable = false)
  private Instant updatedAt;

  @LastModifiedBy
  @Column(nullable = false)
  private String updatedBy;

  /** Null until the entity is first saved, which tells Spring Data that the entity is new. */
  @Version private Long version;

  public Instant getCreatedAt() {
    return createdAt;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public String getUpdatedBy() {
    return updatedBy;
  }

  public Long getVersion() {
    return version;
  }
}
