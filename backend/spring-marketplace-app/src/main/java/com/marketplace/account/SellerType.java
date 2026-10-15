package com.marketplace.account;

/**
 * Platform-operated sellers and third-party sellers share the sales flow; only commission, payout
 * and ledger entries differ (需求 3.2).
 */
public enum SellerType {
  FIRST_PARTY,
  THIRD_PARTY
}
