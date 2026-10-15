package com.marketplace.shared;

import com.marketplace.openapi.model.PageMeta;

/** Turns a {@link PageResult} into the {@code page} member of list responses (設計_005 2.5). */
public final class ApiPages {

  private ApiPages() {}

  public static PageMeta meta(PageResult<?> result) {
    return new PageMeta(
        result.page(),
        result.size(),
        Math.toIntExact(result.totalItems()),
        result.totalItemsCapped(),
        result.totalPages());
  }
}
