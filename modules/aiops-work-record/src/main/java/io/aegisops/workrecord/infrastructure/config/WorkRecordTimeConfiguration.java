package io.aegisops.workrecord.infrastructure.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WorkRecordTimeConfiguration {

  @Bean
  @Qualifier("workRecordClock")
  public Clock workRecordClock(
      @Value("${aiops.work-record.time-zone:Asia/Shanghai}") String timeZone) {
    return Clock.system(ZoneId.of(timeZone));
  }
}