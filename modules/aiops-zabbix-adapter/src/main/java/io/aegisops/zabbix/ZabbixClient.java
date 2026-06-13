package io.aegisops.zabbix;

import java.util.List;

public interface ZabbixClient {
    String testConnection();

    List<ZabbixHost> getHosts(int limit);

    List<ZabbixProblem> getProblems(int limit);
}
