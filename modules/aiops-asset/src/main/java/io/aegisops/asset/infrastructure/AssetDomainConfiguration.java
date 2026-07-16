package io.aegisops.asset.infrastructure;

import io.aegisops.asset.domain.rule.AssetIdentityNormalizer;
import io.aegisops.asset.domain.rule.AssetIdentityResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AssetDomainConfiguration {
  @Bean
  AssetIdentityNormalizer assetIdentityNormalizer() {
    return new AssetIdentityNormalizer();
  }

  @Bean
  AssetIdentityResolver assetIdentityResolver() {
    return new AssetIdentityResolver();
  }
}
