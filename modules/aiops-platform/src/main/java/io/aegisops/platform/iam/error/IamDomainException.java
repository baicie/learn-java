package io.aegisops.platform.iam.error;

import java.util.Map;

/**
 * Thrown by the platform IAM management plane when a domain rule is violated. Controllers map it
 * to the matching HTTP status via {@link IamErrorCode#httpStatus()}.
 */
public class IamDomainException extends RuntimeException {

  private final IamErrorCode code;
  private final Map<String, Object> details;

  public IamDomainException(IamErrorCode code, String message) {
    this(code, message, Map.of());
  }

  public IamDomainException(
      IamErrorCode code, String message, Map<String, Object> details) {
    super(message);
    this.code = code;
    this.details = details == null ? Map.of() : Map.copyOf(details);
  }

  public IamErrorCode code() {
    return code;
  }

  public Map<String, Object> details() {
    return details;
  }
}