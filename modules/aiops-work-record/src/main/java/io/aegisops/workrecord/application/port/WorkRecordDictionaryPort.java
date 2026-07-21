package io.aegisops.workrecord.application.port;

import java.util.List;
import java.util.Map;

public interface WorkRecordDictionaryPort {
  void requireEnabledItem(String tenantId, String dictCode, String itemValue);

  /** 返回当前可用于新记录的启用字典项 value，顺序与平台字典配置一致。 */
  List<String> enabledItemValues(String tenantId, String dictCode);

  /** 返回包含禁用项的 value -> label 映射。禁用项用于历史记录导出回显。 */
  Map<String, String> itemLabels(String tenantId, String dictCode);
}
