package io.aegisops.zabbix;

/**
 * Mapping rules for Zabbix problem severity -> AegisOps alert severity.
 *
 * <p>Zabbix exposes numeric severities 0..5; we collapse them into four buckets
 * that the rest of the platform already understands.
 */
public final class ZabbixSeverity {
    public static final String INFO = "info";
    public static final String WARNING = "warning";
    public static final String CRITICAL = "critical";
    public static final String DISASTER = "disaster";

    private ZabbixSeverity() {
    }

    public static String map(int severity) {
        return switch (severity) {
            case 0, 1 -> INFO;
            case 2, 3 -> WARNING;
            case 4 -> CRITICAL;
            case 5 -> DISASTER;
            default -> INFO;
        };
    }
}
