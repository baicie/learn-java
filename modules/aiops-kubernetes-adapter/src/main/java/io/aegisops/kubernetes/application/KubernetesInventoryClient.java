package io.aegisops.kubernetes.application;

import io.aegisops.kubernetes.domain.model.KubernetesResource;
import java.util.List;

public interface KubernetesInventoryClient {
  String version();

  List<KubernetesResource> listInventory();
}
