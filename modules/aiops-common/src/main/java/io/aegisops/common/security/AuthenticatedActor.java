package io.aegisops.common.security;

/**
 * 跨模块可见的最小认证主体契约。
 *
 * <p>基础模块只能依赖该契约，不得反向依赖 {@code aiops-security} 的具体认证实现。
 */
public interface AuthenticatedActor {
  String id();

  String tenantId();
}
