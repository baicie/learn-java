package io.aegisops.common.exception;

/**
 * 历史遗留的"未找到"异常。新代码请使用 {@link ResourceNotFoundException}。
 *
 * <p>本类保留以维持向后兼容（部分旧 Controller 仍直接抛出），其错误码 {@code "NOT_FOUND"} 与 {@link ErrorCode#NOT_FOUND} 对应，仍按
 * HTTP 404 处理。
 */
public class NotFoundException extends AppException {
  public NotFoundException(String message) {
    super(ErrorCode.NOT_FOUND, message);
  }
}
