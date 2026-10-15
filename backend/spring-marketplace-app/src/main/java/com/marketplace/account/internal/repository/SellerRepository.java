package com.marketplace.account.internal.repository;

import com.marketplace.account.internal.entity.Seller;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SellerRepository extends JpaRepository<Seller, UUID> {

  /**
   * Sellers whose name matches a {@code LIKE} pattern, ignoring case.
   *
   * @param pattern for example {@code %shop%}; {@code \} escapes {@code %} and {@code _}
   */
  @Query("select s from Seller s where lower(s.name) like lower(:pattern) escape '\\'")
  Page<Seller> searchByName(String pattern, Pageable pageable);
}
