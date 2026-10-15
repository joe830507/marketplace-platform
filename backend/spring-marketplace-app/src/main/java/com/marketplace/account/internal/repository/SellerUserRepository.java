package com.marketplace.account.internal.repository;

import com.marketplace.account.internal.entity.SellerUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SellerUserRepository extends JpaRepository<SellerUser, UUID> {

  /** Compares with {@code lower()} so the query uses the case-insensitive unique index. */
  @Query("select u from SellerUser u where lower(u.username) = lower(:username)")
  Optional<SellerUser> findByUsername(String username);
}
