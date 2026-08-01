package io.aegisops.server;

import static org.assertj.core.api.Assertions.assertThat;

import io.aegisops.worker.job.ZabbixSyncJob;
import io.aegisops.worker.outbox.OutboxPoller;
import io.aegisops.worker.runtime.WorkerRuntimeTopology;
import org.junit.jupiter.api.Test;

class AegisOpsAppRuntimeTopologyTest {
  @Test
  void mainApplicationArtifactContainsBackgroundWorkerRuntime() {
    assertThat(WorkerRuntimeTopology.class).isNotNull();
    assertThat(OutboxPoller.class).isNotNull();
    assertThat(ZabbixSyncJob.class).isNotNull();
  }
}
