package com.marketplace.account;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Reads sellers' current commission rates for the seller views of the account module.
 *
 * <p>The rates belong to the settlement module, which depends on the account module. The account
 * module declares this interface and the settlement module implements it, so the two modules do not
 * depend on each other in a cycle (ADR_015).
 */
public interface SellerCommissionRates {

  /**
   * @return the rate in units of 0.01% for each seller that has one; first-party sellers have none
   */
  Map<UUID, Integer> currentRates(Collection<UUID> sellerIds);
}
