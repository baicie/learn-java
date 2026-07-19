package io.aegisops.persistence;

import org.springframework.boot.jooq.autoconfigure.DefaultConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** PostgreSQL runtime settings shared by all jOOQ repositories. */
@Configuration(proxyBeanMethods = false)
public class AegisJooqConfiguration {
  /**
   * Keeps generated DDL metadata schema names out of runtime SQL.
   *
   * @return the shared jOOQ configuration customizer
   */
  @Bean
  public DefaultConfigurationCustomizer jooqConfigurationCustomizer() {
    return configuration -> configuration.settings().withRenderSchema(false);
  }
}
