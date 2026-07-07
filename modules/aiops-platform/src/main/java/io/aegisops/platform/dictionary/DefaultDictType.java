package io.aegisops.platform.dictionary;

/** §6.15.4 列出的默认平台字典类型。 */
public enum DefaultDictType {
  RECORD_TYPE("record_type", "记录类型", "工作记录类型（例：日常、变更、应急响应）"),
  RECORD_STATUS("record_status", "记录状态", "工作记录生命周期状态"),
  RECORD_PRIORITY("record_priority", "处理优先级", "记录处理的紧迫程度"),
  ENV_TYPE("env_type", "环境类型", "生产 / 预发 / 测试 / 本地"),
  YES_NO("yes_no", "是否", "是 / 否 通用选项"),
  PROCESS_RESULT("process_result", "处理结果", "通用处理结果分类");

  private final String code;
  private final String name;
  private final String description;

  DefaultDictType(String code, String name, String description) {
    this.code = code;
    this.name = name;
    this.description = description;
  }

  public String code() {
    return code;
  }

  public String displayName() {
    return name;
  }

  public String description() {
    return description;
  }
}
