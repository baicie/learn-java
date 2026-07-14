package io.aegisops.web.request;

public class RequestBodyTooLargeException extends RuntimeException {

  public RequestBodyTooLargeException(long maxBytes) {
    super("request body exceeds " + maxBytes + " bytes");
  }
}
