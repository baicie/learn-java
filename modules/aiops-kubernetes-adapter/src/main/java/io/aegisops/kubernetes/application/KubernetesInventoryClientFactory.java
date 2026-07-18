package io.aegisops.kubernetes.application;

import io.aegisops.kubernetes.domain.model.KubernetesConfig;

public interface KubernetesInventoryClientFactory {
  KubernetesInventoryClient create(KubernetesConfig config);
}
