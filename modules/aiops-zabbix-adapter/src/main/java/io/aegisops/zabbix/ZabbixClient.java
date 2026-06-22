package io.aegisops.zabbix;

import java.util.List;

public interface ZabbixClient {
  String testConnection();

  List<ZabbixHost> getHosts(int limit);

  List<ZabbixProblem> getProblems(int limit);

  List<ZabbixItem> getItems(ZabbixItemQuery query);

  List<ZabbixHistoryPoint> getHistory(ZabbixHistoryQuery query);

  List<ZabbixTrendPoint> getTrends(ZabbixTrendQuery query);

  List<ZabbixEvent> getEvents(ZabbixEventQuery query);

  List<ZabbixTrigger> getTriggers(ZabbixTriggerQuery query);
}
