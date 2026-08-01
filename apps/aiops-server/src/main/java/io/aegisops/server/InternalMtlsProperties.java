package io.aegisops.server;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aiops.internal-mtls")
public class InternalMtlsProperties {
  private int port = 8443;
  private String certificateFile = "";
  private String privateKeyFile = "";
  private String clientCaFile = "";

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public String getCertificateFile() {
    return certificateFile;
  }

  public void setCertificateFile(String certificateFile) {
    this.certificateFile = certificateFile;
  }

  public String getPrivateKeyFile() {
    return privateKeyFile;
  }

  public void setPrivateKeyFile(String privateKeyFile) {
    this.privateKeyFile = privateKeyFile;
  }

  public String getClientCaFile() {
    return clientCaFile;
  }

  public void setClientCaFile(String clientCaFile) {
    this.clientCaFile = clientCaFile;
  }

  void validate() {
    if (port < 1 || port > 65535) {
      throw new IllegalStateException("aiops.internal-mtls.port must be a valid TCP port");
    }
    requireText(certificateFile, "aiops.internal-mtls.certificate-file is required");
    requireText(privateKeyFile, "aiops.internal-mtls.private-key-file is required");
    requireText(clientCaFile, "aiops.internal-mtls.client-ca-file is required");
  }

  private static void requireText(String value, String message) {
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(message);
    }
  }
}
