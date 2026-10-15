package com.marketplace.account.internal.repository;

import com.marketplace.account.internal.entity.Member;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface MemberRepository extends JpaRepository<Member, UUID> {

  /** Compares with {@code lower()} so the query uses the case-insensitive unique index. */
  @Query("select m from Member m where lower(m.username) = lower(:username)")
  Optional<Member> findByUsername(String username);

  /**
   * Locks the member's row, so that changes to the member's shipping addresses happen one at a time
   * and never leave two defaults.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select m from Member m where m.id = :id")
  Optional<Member> lockById(UUID id);
}
