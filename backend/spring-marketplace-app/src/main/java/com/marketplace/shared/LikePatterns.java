package com.marketplace.shared;

import org.jspecify.annotations.Nullable;

/** Builds SQL {@code LIKE} patterns from user input, with {@code \} as the escape character. */
public final class LikePatterns {

  private LikePatterns() {}

  /** Matches text containing the keyword; matches everything when there is no keyword. */
  public static String contains(@Nullable String keyword) {
    if (keyword == null || keyword.isBlank()) {
      return "%";
    }
    String escaped = keyword.strip().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    return "%" + escaped + "%";
  }
}
