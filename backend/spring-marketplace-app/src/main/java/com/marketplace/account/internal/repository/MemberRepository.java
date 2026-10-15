package com.marketplace.account.internal.repository;

import com.marketplace.account.internal.entity.Member;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MemberRepository extends JpaRepository<Member, UUID> {

  /** Compares with {@code lower()} so the query uses the case-insensitive unique index. */
  @Query("select m from Member m where lower(m.username) = lower(:username)")
  Optional<Member> findByUsername(String username);
}
