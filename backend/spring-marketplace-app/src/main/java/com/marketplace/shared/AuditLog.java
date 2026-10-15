package com.marketplace.shared;

import org.jspecify.annotations.Nullable;

/**
 * Writes level-two audit records (需求 3.6).
 *
 * <p>A record joins the caller's database transaction, so it exists only if the change it describes
 * is committed. Records are never updated or deleted.
 */
public interface AuditLog {

  /**
   * Records a change.
   *
   * @param action what was done, for example {@code LOCK_ACCOUNT}
   * @param before values before the change, serialized as JSON; never passwords or their hashes
   * @param after values after the change, under the same rules as {@code before}
   */
  void record(
      Actor actor,
      String action,
      String targetType,
      String targetId,
      @Nullable Object before,
      @Nullable Object after);
}
