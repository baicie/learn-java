package io.aegisops.common.api;

import java.util.List;

/**
 * 通用分页结果。{@code page} 从 1 开始。
 *
 * @param total 总记录数
 * @param page 当前页（从 1 开始）
 * @param size 每页大小
 * @param items 当前页数据
 */
public record PageResult<T>(long total, int page, int size, List<T> items) {
  public static final int MAX_PAGE_SIZE = 100;

  public static <T> PageResult<T> empty(int page, int size) {
    return new PageResult<>(0L, page, size, List.of());
  }

  /** 规范化分页参数：page 至少为 1，size 在 [1, MAX_PAGE_SIZE] 区间内。 */
  public static int normalizePage(Integer page) {
    if (page == null || page < 1) {
      return 1;
    }
    return page;
  }

  public static int normalizeSize(Integer size) {
    if (size == null || size < 1) {
      return 20;
    }
    return Math.min(size, MAX_PAGE_SIZE);
  }
}
