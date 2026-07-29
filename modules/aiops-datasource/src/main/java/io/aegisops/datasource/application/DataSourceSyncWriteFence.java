package io.aegisops.datasource.application;

import io.aegisops.common.exception.AppException;
import io.aegisops.datasource.application.port.DataSourceSyncStore;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class DataSourceSyncWriteFence {
  private final DataSourceSyncStore syncStore;
  private final TransactionOperations transactions;

  @Autowired
  public DataSourceSyncWriteFence(
      DataSourceSyncStore syncStore, PlatformTransactionManager transactionManager) {
    this(syncStore, new TransactionTemplate(transactionManager));
  }

  DataSourceSyncWriteFence(DataSourceSyncStore syncStore, TransactionOperations transactions) {
    this.syncStore = syncStore;
    this.transactions = transactions;
  }

  public <T> T executeClaimedWrite(
      String tenantId, String datasourceId, String runId, String claimToken, Supplier<T> write) {
    return transactions.execute(
        status -> {
          OffsetDateTime leaseUntil = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5);
          if (!syncStore.renewClaim(tenantId, datasourceId, runId, claimToken, leaseUntil)) {
            throw new AppException(
                "DATASOURCE_SYNC_CLAIM_LOST", "Datasource sync run was claimed by another worker");
          }
          return write.get();
        });
  }
}
