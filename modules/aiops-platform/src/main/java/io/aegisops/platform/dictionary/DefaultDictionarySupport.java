package io.aegisops.platform.dictionary;

import java.util.List;

/** 提供 tenant 列表的 SPI，避免 initializer 直接依赖具体 ORM/Repository 类型。 */
public interface DefaultDictionarySupport {
  List<String> listActiveTenants();
}
