package com.marketplace.account.internal.repository;

import com.marketplace.account.internal.entity.OperatorUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OperatorUserRepository extends JpaRepository<OperatorUser, UUID> {

  /** Compares with {@code lower()} so the query uses the case-insensitive unique index. */
  @Query("select u from OperatorUser u where lower(u.username) = lower(:username)")
  Optional<OperatorUser> findByUsername(String username);
}
