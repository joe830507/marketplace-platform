package com.marketplace.shared;

import java.util.List;
import java.util.function.Function;

/**
 * One page of a list with its totals (設計_005 2.5). When more items match than the list may be read,
 * the total is cut at the limit and {@code totalItemsCapped} is true.
 */
public record PageResult<T>(
    List<T> items, int page, int size, long totalItems, boolean totalItemsCapped) {

  public PageResult {
    items = List.copyOf(items);
  }

  /**
   * @param matching how many items match; may be counted only up to {@code paging.maxItems() + 1}
   */
  public static <T> PageResult<T> of(List<T> items, Paging paging, long matching) {
    boolean capped = matching > paging.maxItems();
    return new PageResult<>(
        items, paging.page(), paging.size(), capped ? paging.maxItems() : matching, capped);
  }

  public int totalPages() {
    return (int) ((totalItems + size - 1) / size);
  }

  public <R> PageResult<R> map(Function<? super T, ? extends R> mapper) {
    return new PageResult<>(
        items.stream().<R>map(mapper).toList(), page, size, totalItems, totalItemsCapped);
  }
}
