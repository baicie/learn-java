package io.aegisops.workrecord.domain.model;

/**
 * 工作记录状态常量。
 *
 * <p>本枚举集中固化所有工作记录状态字符串字面量，避免散落在 Service / Repository / 测试 各个位置的 {@code "draft"} / {@code
 * "processing"} 拼写漂移。
 *
 * <p>与平台字典 {@code record_status}（V0012 seed）的 value 必须保持一致；新增状态需：
 *
 * <ol>
 *   <li>在本类加常量
 *   <li>新增 Flyway migration 同步字典项
 *   <li>前端 i18n 加 {@code workRecords.status.<value>} key
 * </ol>
 *
 * <p>历史命名差异（参见 Phase 01–07 实施审查报告 §4.5 / §5.5）： Phase WR-2 design doc 使用 {@code submitted}，实现迁移时改为
 * {@code processing}。本期工作 统一以 {@link #PROCESSING} 为权威值，前端 i18n 也对应 {@code
 * workRecords.status.processing}。 后续 Phase WR-S2 如需引入更细粒度的 submitted / in_review 状态，应沿用本枚举新增常量，
 * 不得再硬编码字符串。
 */
public final class RecordStatus {
  public static final String DRAFT = "draft";
  public static final String PROCESSING = "processing";
  public static final String DONE = "done";
  public static final String ARCHIVED = "archived";

  private RecordStatus() {}
}
