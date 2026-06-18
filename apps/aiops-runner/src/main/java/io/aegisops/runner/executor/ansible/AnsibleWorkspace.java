package io.aegisops.runner.executor.ansible;

import java.nio.file.Path;

public record AnsibleWorkspace(Path root, Path inventoryFile, Path playbookFile) {}
