package com.marketplace.shared;

import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The page a list query asks for (需求 3.11, 設計_005 2.5): 20, 50 or 100 items per page, and never
 * past the list's depth limit.
 *
 * @param page the page number, from 1
 * @param size items per page
 * @param maxItems how deep the list may be read: 10,000 items, or 1,000 for search
 */
public record Paging(int page, int size, int maxItems) {

  public static final int DEFAULT_SIZE = 20;

  /** Lists other than search may be read up to this many items. */
  public static final int MAX_ITEMS = 10_000;

  /** Category browsing and keyword search may be read up to this many items. */
  public static final int MAX_SEARCH_ITEMS = 1_000;

  private static final Set<Integer> SIZES = Set.of(20, 50, 100);

  /**
   * Validates the query parameters; a missing value takes its default.
   *
   * @throws ApiException {@code VALIDATION_FAILED} for an unsupported size or a page past the limit
   */
  public static Paging of(@Nullable Integer page, @Nullable Integer size, int maxItems) {
    int pageNumber = page == null ? 1 : page;
    int pageSize = size == null ? DEFAULT_SIZE : size;
    if (pageNumber < 1) {
      throw ApiException.invalidField("page", "頁碼從 1 開始");
    }
    if (!SIZES.contains(pageSize)) {
      throw ApiException.invalidField("size", "每頁筆數只能是 20、50 或 100");
    }
    Paging paging = new Paging(pageNumber, pageSize, maxItems);
    if (paging.offset() >= maxItems) {
      throw ApiException.invalidField("page", "最多只能查詢到第 %,d 筆".formatted(maxItems));
    }
    return paging;
  }

  /** The number of items before this page. */
  public long offset() {
    return (long) (page - 1) * size;
  }

  /** How many items to fetch for this page without reading past the depth limit. */
  public int limit() {
    return (int) Math.min(size, maxItems - offset());
  }
}
