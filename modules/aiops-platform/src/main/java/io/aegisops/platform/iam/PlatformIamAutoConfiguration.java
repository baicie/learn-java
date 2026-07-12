package io.aegisops.platform.iam;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

/**
 * Activates the Portal IAM management plane: controllers, services and repositories under
 * {@code io.aegisops.platform.iam}.
 */
@Configuration
@ComponentScan(
    basePackages = "io.aegisops.platform.iam",
    useDefaultFilters = false,
    includeFilters = {
      @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = org.springframework.stereotype.Component.class),
      @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = org.springframework.stereotype.Service.class),
      @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = org.springframework.stereotype.Repository.class),
      @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = org.springframework.web.bind.annotation.RestControllerAdvice.class),
      @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = org.springframework.web.bind.annotation.RestController.class)
    })
@ConditionalOnMissingBean(name = "platformIamAutoConfiguration")
public class PlatformIamAutoConfiguration {
  // Trigger: PackageScan marker. The class itself is the @Configuration that bootstraps the
  // IAM management plane component graph. Other modules only need to put
  // META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports entry below
  // (see PlatformIamAutoConfiguration.imports).
}