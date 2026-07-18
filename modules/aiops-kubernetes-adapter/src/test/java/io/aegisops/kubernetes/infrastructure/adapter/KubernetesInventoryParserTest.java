package io.aegisops.kubernetes.infrastructure.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class KubernetesInventoryParserTest {
  @Test
  void parsesNodeIdentityAndOwnerReferences() {
    String json =
        """
        {"items":[{"metadata":{"uid":"node-1","name":"worker-1","labels":{"zone":"a"},
        "ownerReferences":[{"uid":"cluster-1"}]},"spec":{"providerID":"aws:///i-1"},
        "status":{"nodeInfo":{"machineID":"machine-1"},"addresses":[{"type":"InternalIP","address":"10.0.0.1"}]}}]}
        """;
    var resource = new KubernetesInventoryParser(new ObjectMapper()).parse("Node", json).getFirst();
    assertEquals("node-1", resource.uid());
    assertEquals("machine-1", resource.machineId());
    assertEquals("aws:///i-1", resource.providerId());
    assertEquals("10.0.0.1", resource.ip());
    assertEquals("cluster-1", resource.ownerUids().getFirst());
  }

  @Test
  void parsesAllSupportedInventoryKinds() {
    var parser = new KubernetesInventoryParser(new ObjectMapper());
    for (String kind :
        java.util.List.of(
            "Namespace", "Deployment", "StatefulSet", "DaemonSet", "Pod", "Service", "Ingress")) {
      String json =
          "{\"items\":[{\"metadata\":{\"uid\":\""
              + kind
              + "-1\",\"name\":\"sample\",\"namespace\":\"prod\"}}]}";
      assertEquals(kind, parser.parse(kind, json).getFirst().kind());
    }
  }
}
