package com.marketplace.account.internal.repository;

import com.marketplace.account.internal.entity.Seller;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SellerRepository extends JpaRepository<Seller, UUID> {}
