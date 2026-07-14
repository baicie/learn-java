package io.aegisops.workrecord.infrastructure.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WorkRecordMetricsSnapshotJob {

  private static final Logger log = LoggerFactory.getLogger(WorkRecordMetricsSnapshotJob.class);

  private final NamedParameterJdbcTemplate jdbc;

  private final AtomicLong templates = new AtomicLong();

  private final AtomicLong fields = new AtomicLong();

  private final AtomicLong records = new AtomicLong();

  public WorkRecordMetricsSnapshotJob(NamedParameterJdbcTemplate jdbc, MeterRegistry registry) {
    this.jdbc = jdbc;

    Gauge.builder("aegisops_work_record_templates", templates, AtomicLong::get)
        .description("Current enabled/non-deleted work-record templates")
        .register(registry);

    Gauge.builder("aegisops_work_record_fields", fields, AtomicLong::get)
        .description("Current work-record field index rows")
        .register(registry);

    Gauge.builder("aegisops_work_record_records", records, AtomicLong::get)
        .description("Current non-deleted work records")
        .register(registry);
  }

  @PostConstruct
  void initialize() {
    refresh();
  }

  @Scheduled(fixedDelayString = "${aiops.work-record.metrics.snapshot-delay-ms:60000}")
  public void refresh() {
    try {
      Map<String, Object> values =
          jdbc.queryForMap(
              """
              select
                  (
                      select count(*)
                      from work_record.wr_template
                      where deleted_at is null
                  ) as template_count,
                  (
                      select count(*)
                      from work_record.wr_template_field
                  ) as field_count,
                  (
                      select count(*)
                      from work_record.wr_record
                      where deleted_at is null
                  ) as record_count
              """,
              Map.of());

      templates.set(number(values.get("template_count")));
      fields.set(number(values.get("field_count")));
      records.set(number(values.get("record_count")));
    } catch (RuntimeException ex) {
      log.warn("failed to refresh work-record metrics", ex);
    }
  }

  private long number(Object value) {
    return value instanceof Number number ? number.longValue() : 0;
  }
}
