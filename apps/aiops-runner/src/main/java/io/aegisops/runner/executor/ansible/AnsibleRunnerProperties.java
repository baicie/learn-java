package io.aegisops.runner.executor.ansible;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aiops.runner.ansible")
public class AnsibleRunnerProperties {
  private boolean checkExecutionEnabled = true;
  private String binary = "ansible-playbook";
  private Path workspaceRoot = Path.of(".runtime", "ansible", "workspaces");
  private Path resourceRoot = Path.of(".runtime", "ansible", "resources");
  private boolean cleanupWorkspace = true;
  private int maxOutputChars = 65536;
  private int maxMaterializedFileBytes = 1048576;

  public boolean isCheckExecutionEnabled() {
    return checkExecutionEnabled;
  }

  public void setCheckExecutionEnabled(boolean checkExecutionEnabled) {
    this.checkExecutionEnabled = checkExecutionEnabled;
  }

  public String getBinary() {
    return binary;
  }

  public void setBinary(String binary) {
    this.binary = binary;
  }

  public Path getWorkspaceRoot() {
    return workspaceRoot;
  }

  public void setWorkspaceRoot(Path workspaceRoot) {
    this.workspaceRoot = workspaceRoot;
  }

  public Path getResourceRoot() {
    return resourceRoot;
  }

  public void setResourceRoot(Path resourceRoot) {
    this.resourceRoot = resourceRoot;
  }

  public boolean isCleanupWorkspace() {
    return cleanupWorkspace;
  }

  public void setCleanupWorkspace(boolean cleanupWorkspace) {
    this.cleanupWorkspace = cleanupWorkspace;
  }

  public int getMaxOutputChars() {
    return maxOutputChars;
  }

  public void setMaxOutputChars(int maxOutputChars) {
    this.maxOutputChars = maxOutputChars;
  }

  public int getMaxMaterializedFileBytes() {
    return maxMaterializedFileBytes;
  }

  public void setMaxMaterializedFileBytes(int maxMaterializedFileBytes) {
    this.maxMaterializedFileBytes = maxMaterializedFileBytes;
  }

  public int normalizedMaxOutputChars() {
    return Math.max(1024, Math.min(maxOutputChars, 1024 * 1024));
  }

  public int normalizedMaxMaterializedFileBytes() {
    return Math.max(1024, Math.min(maxMaterializedFileBytes, 10 * 1024 * 1024));
  }
}
