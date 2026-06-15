package io.aegisops.datasource;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Bounded executor that protects the Tomcat HTTP thread pool from being starved by long-running
 * datasource sync operations. The sync endpoint dispatches the work to {@code
 * datasourceSyncExecutor} and waits at most {@link #SYNC_TIMEOUT_SECONDS} seconds for completion.
 * Anything beyond that is returned as a structured error to the caller instead of hanging the HTTP
 * connection.
 *
 * <p>This is the Phase 1.0 stop-gap. The real fix lives in Phase 1.1, when the sync path moves to
 * {@code aiops-worker} with persistent run state.
 */
@Configuration
public class DatasourceSyncExecutorConfig {

  public static final String SYNC_EXECUTOR = "datasourceSyncExecutor";
  public static final int SYNC_TIMEOUT_SECONDS = 30;

  @Bean(name = SYNC_EXECUTOR, destroyMethod = "shutdown")
  public ThreadPoolTaskExecutor datasourceSyncExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(8);
    executor.setThreadNamePrefix("datasource-sync-");
    executor.setKeepAliveSeconds(60);
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(30);
    executor.initialize();
    return executor;
  }
}
