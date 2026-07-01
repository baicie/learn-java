package io.aegisops.platform;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PlatformModuleService {
  private final PlatformModuleRepository repository;

  public PlatformModuleService(PlatformModuleRepository repository) {
    this.repository = repository;
  }

  public List<PlatformModule> listModules() {
    return repository.findAll();
  }

  public PlatformModule getOrCreatePlatform() {
    return repository
        .findByModuleId("platform")
        .orElseGet(
            () ->
                repository.create(
                    "platform", "平台底座", "1.0.0"));
  }
}
