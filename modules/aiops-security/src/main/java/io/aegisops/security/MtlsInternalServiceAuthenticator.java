package io.aegisops.security;

import jakarta.servlet.http.HttpServletRequest;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;

public final class MtlsInternalServiceAuthenticator implements InternalServiceAuthenticator {
  private static final String CERTIFICATE_ATTRIBUTE = "jakarta.servlet.request.X509Certificate";
  private static final int URI_SAN_TYPE = 6;

  private final AiopsSecurityProperties properties;

  public MtlsInternalServiceAuthenticator(AiopsSecurityProperties properties) {
    properties.validateInternalAgentCertificateIdentities();
    this.properties = properties;
  }

  @Override
  public InternalServicePrincipal authenticate(HttpServletRequest request) {
    Object attribute = request.getAttribute(CERTIFICATE_ATTRIBUTE);
    if (!(attribute instanceof X509Certificate[] certificates) || certificates.length == 0) {
      throw new InternalServiceAuthenticationException("TLS client certificate is required");
    }

    String identity = findAllowedUriIdentity(certificates[0]);
    if (identity == null) {
      throw new InternalServiceAuthenticationException(
          "TLS client certificate identity is not authorized");
    }
    return new InternalServicePrincipal(identity, java.util.Set.of());
  }

  private String findAllowedUriIdentity(X509Certificate certificate) {
    try {
      Collection<List<?>> names = certificate.getSubjectAlternativeNames();
      if (names == null) {
        return null;
      }
      for (List<?> name : names) {
        if (name.size() >= 2
            && name.get(0) instanceof Integer type
            && type == URI_SAN_TYPE
            && name.get(1) instanceof String identity
            && properties.getInternalAgentCertificateIdentities().contains(identity)) {
          return identity;
        }
      }
      return null;
    } catch (CertificateParsingException exception) {
      throw new InternalServiceAuthenticationException(
          "Unable to parse TLS client certificate identity", exception);
    }
  }
}
