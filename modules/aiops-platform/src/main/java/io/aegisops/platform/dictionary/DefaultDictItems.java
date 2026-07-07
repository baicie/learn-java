package io.aegisops.platform.dictionary;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** §6.15.4 列出的默认平台字典项。注意 item_value 字段语义稳定，不要随意改写：会被记录字段 option 引用，DB 中存的是字符串值。 */
public final class DefaultDictItems {

  private DefaultDictItems() {}

  public static Map<String, List<DefaultDictItem>> itemsByType() {
    Map<String, List<DefaultDictItem>> map = new LinkedHashMap<>();
    map.put(
        "record_type",
        List.of(
            item("日常巡检", "daily", "gray", "日常巡检与值班记录", 10),
            item("变更执行", "change", "blue", "变更窗口内执行结果", 20),
            item("应急响应", "incident", "red", "线上应急处置全过程", 30)));
    map.put(
        "record_status",
        List.of(
            item("草稿", "draft", "gray", "尚未确认或补充完整", 10),
            item("处理中", "processing", "orange", "已认领并在跟进", 20),
            item("已完成", "done", "green", "处理完成且已复盘", 30),
            item("已归档", "archived", "default", "已沉淀进案例库", 40)));
    map.put(
        "record_priority",
        List.of(
            item("低", "low", "gray", "低优先级，可批量处理", 10),
            item("中", "medium", "orange", "常规优先级", 20),
            item("高", "high", "red", "高优先级，4 小时内处理", 30),
            item("紧急", "urgent", "magenta", "立即处理", 40)));
    map.put(
        "env_type",
        List.of(
            item("生产", "prod", "red", "面向线上真实流量", 10),
            item("预发", "staging", "orange", "预发验证环境", 20),
            item("测试", "test", "blue", "集成测试", 30),
            item("本地", "dev", "gray", "本地开发", 40)));
    map.put(
        "yes_no", List.of(item("是", "yes", "green", "肯定", 10), item("否", "no", "gray", "否定", 20)));
    map.put(
        "process_result",
        List.of(
            item("已解决", "resolved", "green", "问题已修复", 10),
            item("已缓解", "mitigated", "blue", "风险已控制", 20),
            item("持续观察", "observed", "orange", "需后续跟踪", 30),
            item("已升级", "escalated", "red", "已上报更高一级", 40)));
    return map;
  }

  private static DefaultDictItem item(
      String label, String value, String color, String description, int sortOrder) {
    return new DefaultDictItem(label, value, color, description, sortOrder);
  }
}
