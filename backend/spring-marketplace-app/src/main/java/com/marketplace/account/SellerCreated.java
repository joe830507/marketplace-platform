package com.marketplace.account;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * An operator created a seller. Published inside the creating transaction; the settlement module
 * stores the commission rate from it in the same transaction (ADR_015).
 *
 * @param commissionRate in units of 0.01%; present only for third-party sellers
 */
public record SellerCreated(
    UUID sellerId, SellerType sellerType, @Nullable Integer commissionRate) {}
