package io.aegisops.observability;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aiops.observability")
public class ObservabilityProperties {
  private boolean enabled = true;
  private String serviceName = "aegisops";
  private String requestIdHeader = "X-Request-Id";
  private String traceIdHeader = "X-Trace-Id";
  private boolean httpMetricsEnabled = true;
  private boolean requestIdResponseHeaderEnabled = true;
  private boolean operationLogEnabled = true;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getServiceName() {
    return serviceName;
  }

  public void setServiceName(String serviceName) {
    this.serviceName = serviceName;
  }

  public String getRequestIdHeader() {
    return requestIdHeader;
  }

  public void setRequestIdHeader(String requestIdHeader) {
    this.requestIdHeader = requestIdHeader;
  }

  public String getTraceIdHeader() {
    return traceIdHeader;
  }

  public void setTraceIdHeader(String traceIdHeader) {
    this.traceIdHeader = traceIdHeader;
  }

  public boolean isHttpMetricsEnabled() {
    return httpMetricsEnabled;
  }

  public void setHttpMetricsEnabled(boolean httpMetricsEnabled) {
    this.httpMetricsEnabled = httpMetricsEnabled;
  }

  public boolean isRequestIdResponseHeaderEnabled() {
    return requestIdResponseHeaderEnabled;
  }

  public void setRequestIdResponseHeaderEnabled(boolean requestIdResponseHeaderEnabled) {
    this.requestIdResponseHeaderEnabled = requestIdResponseHeaderEnabled;
  }

  public boolean isOperationLogEnabled() {
    return operationLogEnabled;
  }

  public void setOperationLogEnabled(boolean operationLogEnabled) {
    this.operationLogEnabled = operationLogEnabled;
  }
}
