package io.aegisops.web.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
@EnableConfigurationProperties(RequestBodyLimitProperties.class)
public class RequestBodyLimitConfiguration {

  @Bean
  RequestBodySizeLimitFilter requestBodySizeLimitFilter(
      RequestBodyLimitProperties properties, ObjectMapper objectMapper) {
    return new RequestBodySizeLimitFilter(properties, objectMapper);
  }

  @Bean
  FilterRegistrationBean<RequestBodySizeLimitFilter> requestBodySizeLimitFilterRegistration(
      RequestBodySizeLimitFilter filter) {
    FilterRegistrationBean<RequestBodySizeLimitFilter> registration =
        new FilterRegistrationBean<>(filter);

    // RequestIdFilter 为 HIGHEST_PRECEDENCE + 5。
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);

    registration.addUrlPatterns("/api/*", "/internal/*");

    return registration;
  }
}
