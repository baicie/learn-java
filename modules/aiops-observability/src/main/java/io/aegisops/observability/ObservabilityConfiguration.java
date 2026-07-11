package io.aegisops.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
@EnableConfigurationProperties(ObservabilityProperties.class)
public class ObservabilityConfiguration {
  @Bean
  RequestIdFilter requestIdFilter(ObservabilityProperties properties) {
    return new RequestIdFilter(properties);
  }

  @Bean
  TenantMdcFilter tenantMdcFilter(ObservabilityProperties properties) {
    return new TenantMdcFilter(properties);
  }

  @Bean
  HttpMetricsFilter httpMetricsFilter(MeterRegistry registry, ObservabilityProperties properties) {
    return new HttpMetricsFilter(registry, properties);
  }

  @Bean
  FilterRegistrationBean<RequestIdFilter> requestIdFilterRegistration(RequestIdFilter filter) {
    FilterRegistrationBean<RequestIdFilter> registration = new FilterRegistrationBean<>(filter);
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 5);
    registration.addUrlPatterns("/*");
    return registration;
  }

  @Bean
  FilterRegistrationBean<TenantMdcFilter> tenantMdcFilterRegistration(TenantMdcFilter filter) {
    FilterRegistrationBean<TenantMdcFilter> registration = new FilterRegistrationBean<>(filter);
    registration.setOrder(Ordered.LOWEST_PRECEDENCE - 20);
    registration.addUrlPatterns("/*");
    return registration;
  }

  @Bean
  FilterRegistrationBean<HttpMetricsFilter> httpMetricsFilterRegistration(
      HttpMetricsFilter filter) {
    FilterRegistrationBean<HttpMetricsFilter> registration = new FilterRegistrationBean<>(filter);
    registration.setOrder(Ordered.LOWEST_PRECEDENCE - 10);
    registration.addUrlPatterns("/*");
    return registration;
  }

  @Bean
  OperationLogFilter operationLogFilter(ObservabilityProperties properties) {
    return new OperationLogFilter(properties);
  }

  @Bean
  FilterRegistrationBean<OperationLogFilter> operationLogFilterRegistration(
      OperationLogFilter filter) {
    FilterRegistrationBean<OperationLogFilter> registration = new FilterRegistrationBean<>(filter);
    registration.setOrder(Ordered.LOWEST_PRECEDENCE - 5);
    registration.addUrlPatterns("/*");
    return registration;
  }

  @Bean("aegisopsReadiness")
  HealthIndicator aegisopsReadinessIndicator(ObservabilityProperties properties) {
    return new AegisOpsReadinessIndicator(properties);
  }
}
