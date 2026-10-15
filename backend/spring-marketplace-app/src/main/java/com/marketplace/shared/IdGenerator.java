package com.marketplace.shared;

import java.util.UUID;

/** Generates primary keys in the application, before data is written (ADR_010). */
public interface IdGenerator {

  /** A UUIDv7 whose leading bits are the current time, so new keys sort after older ones. */
  UUID newId();
}
