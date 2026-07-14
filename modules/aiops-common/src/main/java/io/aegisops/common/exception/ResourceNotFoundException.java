package io.aegisops.common.exception;

/**
 * 表示请求所操作的资源不存在。
 *
 * <p>映射到 {@link ErrorCode#RESOURCE_NOT_FOUND} (HTTP 404)。
 *
 * <p>区别于 {@link NotFoundException}：后者沿用历史 {@code "NOT_FOUND"} 字符串码， 仍按 400 处理。新代码必须使用本类型以保证对外错误码一致。
 */
public final class ResourceNotFoundException extends AppException {

  public ResourceNotFoundException(String message) {
    super(ErrorCode.RESOURCE_NOT_FOUND, message);
  }
}
