package com.marketplace.account.internal.repository;

import com.marketplace.account.internal.entity.ShippingAddress;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ShippingAddressRepository extends JpaRepository<ShippingAddress, UUID> {

  /** The member's addresses that are not deleted, the default first, then newest first. */
  @Query(
      """
      select a from ShippingAddress a
      where a.memberId = :memberId and a.deletedAt is null
      order by a.isDefault desc, a.createdAt desc, a.id desc
      """)
  List<ShippingAddress> findActive(UUID memberId);

  @Query(
      """
      select a from ShippingAddress a
      where a.id = :id and a.memberId = :memberId and a.deletedAt is null
      """)
  Optional<ShippingAddress> findActive(UUID memberId, UUID id);

  /**
   * Clears the member's current default before another address becomes the default. Runs at once,
   * so the unique index on the default never sees two defaults.
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
      update ShippingAddress a
      set a.isDefault = false, a.updatedAt = :now, a.updatedBy = :actor,
          a.version = a.version + 1
      where a.memberId = :memberId and a.isDefault = true and a.deletedAt is null
      """)
  int clearDefault(UUID memberId, Instant now, String actor);
}
