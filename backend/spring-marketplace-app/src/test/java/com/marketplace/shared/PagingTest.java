package com.marketplace.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Paging rules shared by every list (需求 3.11, 設計_005 2.5). */
class PagingTest {

  @Test
  @DisplayName("未指定時為第 1 頁、每頁 20 筆")
  void defaults() {
    Paging paging = Paging.of(null, null, Paging.MAX_ITEMS);

    assertThat(paging.page()).isEqualTo(1);
    assertThat(paging.size()).isEqualTo(20);
    assertThat(paging.offset()).isZero();
  }

  @Test
  @DisplayName("Q4 每頁 100 筆並跳到第 3 頁：回應第 201 到 300 筆")
  void jumpToPage() {
    Paging paging = Paging.of(3, 100, Paging.MAX_ITEMS);

    assertThat(paging.offset()).isEqualTo(200);
    assertThat(paging.limit()).isEqualTo(100);
  }

  @ParameterizedTest(name = "每頁 {0} 筆")
  @ValueSource(ints = {0, 10, 30, 101})
  @DisplayName("Q4 不支援的每頁筆數：請求被拒絕")
  void unsupportedSize(int size) {
    assertThatThrownBy(() -> Paging.of(1, size, Paging.MAX_ITEMS))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
  }

  @Test
  @DisplayName("Q1 翻頁過深：第 1,000 筆之後的搜尋結果被拒絕")
  void pastTheSearchLimit() {
    assertThat(Paging.of(50, 20, Paging.MAX_SEARCH_ITEMS).offset()).isEqualTo(980);
    assertThatThrownBy(() -> Paging.of(51, 20, Paging.MAX_SEARCH_ITEMS))
        .isInstanceOf(ApiException.class);
  }

  @Test
  @DisplayName("Q4 結果過多：總筆數只計算到 10,000 筆並標示 totalItemsCapped")
  void cappedTotal() {
    Paging paging = Paging.of(1, 20, Paging.MAX_ITEMS);

    PageResult<String> result = PageResult.of(List.of("a"), paging, 10_001);

    assertThat(result.totalItems()).isEqualTo(10_000);
    assertThat(result.totalItemsCapped()).isTrue();
    assertThat(result.totalPages()).isEqualTo(500);
  }

  @Test
  @DisplayName("總頁數無條件進位")
  void totalPages() {
    PageResult<String> result = PageResult.of(List.of(), Paging.of(1, 20, Paging.MAX_ITEMS), 41);

    assertThat(result.totalItemsCapped()).isFalse();
    assertThat(result.totalPages()).isEqualTo(3);
  }
}
