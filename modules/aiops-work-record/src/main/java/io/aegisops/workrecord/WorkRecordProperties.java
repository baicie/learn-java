package io.aegisops.workrecord;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 工作记录模块配置项。
 *
 * <p>通过 {@code aiops.work-record.export.max-rows} 绑定，默认 5000。允许运维在不重启
 * 服务的前提下调整导出上限（受限于 application.yml reload 机制）。
 *
 * <p>本类属于基础底座，不携带业务规则；约束与执行由 {@link WorkRecordExportService} 强制。
 */
@ConfigurationProperties(prefix = "aiops.work-record")
public class WorkRecordProperties {

  /** 导出模块配置。 */
  private Export export = new Export();

  public Export getExport() {
    return export;
  }

  public void setExport(Export export) {
    this.export = export;
  }

  public static class Export {
    /** 单次导出最大行数。 */
    private int maxRows = 5000;

    public int getMaxRows() {
      return maxRows;
    }

    public void setMaxRows(int maxRows) {
      this.maxRows = maxRows;
    }
  }
}
