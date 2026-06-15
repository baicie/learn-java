package io.aegisops.web;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebAutoConfiguration {
  @Bean
  public FilterRegistrationBean<RequestIdFilter> requestIdFilter() {
    FilterRegistrationBean<RequestIdFilter> bean = new FilterRegistrationBean<>();
    bean.setFilter(new RequestIdFilter());
    bean.setOrder(-100);
    return bean;
  }
}
