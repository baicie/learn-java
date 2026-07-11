package io.aegisops.common.exception;

/**
 * 表示请求与资源当前状态冲突（例如尝试编辑已归档模板、启用已归档模板、模板被引用时归档）。
 *
 * <p>映射到 {@link ErrorCode#CONFLICT} (HTTP 409)。
 *
 * <p>不要使用 {@link IllegalStateException} 抛出此类错误：
 * {@code IllegalStateException} 也用于 SHA-256 不可用、审计序列化失败等真正的服务端内部错误，
 * 一律映射成 409 会隐藏真实故障。
 */
public final class ConflictException extends AppException {

  public ConflictException(String message) {
    super(ErrorCode.CONFLICT, message);
  }

  public ConflictException(String message, Throwable cause) {
    super(ErrorCode.CONFLICT, message, cause);
  }
}
