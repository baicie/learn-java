package io.aegisops.server.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.security.AuthorizationService;
import io.aegisops.server.AiOpsServerApplication;
import io.aegisops.tenant.TenantRepository;
import io.aegisops.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
    classes = AiOpsServerApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("acceptance")
@Testcontainers
class WorkRecordEnterpriseAcceptanceIT {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("aiops.work-record.export.max-rows", () -> 2);
  }

  @Autowired private TestRestTemplate rest;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private AuthorizationService authorizationService;
  @Autowired private PasswordEncoder passwordEncoder;

  private AcceptanceIdentityFixture.Identities identities;
  private AcceptanceHttpClient api;
  private Tokens tokens;
  private ScenarioState state;

  @BeforeEach
  void prepareIdentities() {
    identities =
        new AcceptanceIdentityFixture(
                tenantRepository, userRepository, authorizationService, passwordEncoder)
            .create();

    api = new AcceptanceHttpClient(rest, objectMapper);

    tokens =
        new Tokens(
            api.login(identities.admin().username(), identities.admin().password()),
            api.login(identities.userA().username(), identities.userA().password()),
            api.login(identities.userB().username(), identities.userB().password()));

    assertAdminAuthorization();

    state = new ScenarioState(identities.suffix());
  }

  @Test
  void completeEnterpriseAcceptanceScenario() {
    step("01 创建默认字典", this::createDefaultDictionary);
    step("02 创建工作日历", this::createWorkCalendar);
    step("03 创建并发布模板 v1", this::createAndPublishTemplateV1);
    step("04 用户填写 v1 记录", this::usersCreateV1Records);
    step("05 管理员查看全部记录", this::administratorCanReadAllRecords);
    step("05.1 无模板条件统计", this::analyticsWithoutTemplateFilterWorks);
    step("06 普通用户只能查看自己的记录", this::normalUserCanOnlyReadSelfRecords);
    step("07 管理员发布模板 v2", this::administratorPublishesTemplateV2);
    step("08 历史记录仍绑定 v1", this::historicalRecordStillUsesV1);
    step("09 新记录使用 v2", this::createNewRecordUsingV2);
    step("10 禁用字典项后历史 label 保留", this::disabledDictionaryItemKeepsHistoricalLabel);
    step("11 动态字段筛选", this::dynamicFieldFilterWorks);
    step("12 动态字段导出", this::dynamicFieldsCanBeExported);
    step("13 导出超限", this::exportLimitIsEnforced);
    step("14 审计完整性", this::auditTrailIsComplete);
  }

  private void step(String name, Runnable action) {
    try {
      action.run();
    } catch (Throwable throwable) {
      throw new AssertionError("Phase 18 acceptance step failed: " + name, throwable);
    }
  }

  private void createDefaultDictionary() {
    state.dictCode = "acc_priority_" + state.suffix;

    api.postData(
        "/api/platform/dictionaries",
        tokens.admin(),
        Map.of(
            "dictCode",
            state.dictCode,
            "dictName",
            "验收优先级",
            "description",
            "Phase 18 企业验收字典",
            "sortOrder",
            10,
            "enabled",
            true));

    api.postData(
        "/api/platform/dictionaries/" + state.dictCode + "/items",
        tokens.admin(),
        Map.of(
            "itemLabel", "高",
            "itemValue", "P1",
            "sortOrder", 10,
            "enabled", true,
            "extraJson", "{}"));

    JsonNode p2 =
        api.postData(
            "/api/platform/dictionaries/" + state.dictCode + "/items",
            tokens.admin(),
            Map.of(
                "itemLabel", "中",
                "itemValue", "P2",
                "sortOrder", 20,
                "enabled", true,
                "extraJson", "{}"));

    state.p2ItemId = p2.path("id").asText();
    assertThat(state.p2ItemId).isNotBlank();
  }

  private void createWorkCalendar() {
    int year = Year.now(ZoneId.of("Asia/Shanghai")).getValue();

    JsonNode calendar =
        api.postData(
            "/api/platform/calendars",
            tokens.admin(),
            Map.of(
                "calendarCode",
                "acc_cn_" + year + "_" + state.suffix,
                "calendarName",
                "验收工作日历 " + year,
                "regionCode",
                "CN",
                "timezone",
                "Asia/Shanghai",
                "year",
                year,
                "enabled",
                true,
                "sourceType",
                "acceptance",
                "description",
                "Phase 18 验收日历"));

    state.calendarId = calendar.path("id").asText();
  }

  private void createAndPublishTemplateV1() {
    JsonNode template =
        api.postData(
            "/api/work-record/templates",
            tokens.admin(),
            Map.of(
                "code",
                "acceptance_daily_" + state.suffix,
                "name",
                "企业验收日报",
                "description",
                "Phase 18 企业验收模板",
                "schemaJson",
                WorkRecordAcceptanceSchemas.v1(state.dictCode),
                "designerJson",
                WorkRecordAcceptanceSchemas.designerV1()));

    state.templateId = template.path("id").asText();

    JsonNode version =
        api.postData(
            "/api/work-record/templates/" + state.templateId + "/publish",
            tokens.admin(),
            Map.of("versionName", "v1"));

    state.v1Id = version.path("id").asText();

    assertThat(version.path("versionNo").asInt()).isEqualTo(1);
  }

  private void usersCreateV1Records() {
    state.userAOldTitle = "用户 A v1 日报 " + state.suffix;
    state.userBOtherTitle = "用户 B v1 日报 " + state.suffix;

    state.userARecordV1 =
        createRecord(
            identities.userA(),
            tokens.userA(),
            state.v1Id,
            state.userAOldTitle,
            Map.of("summary", "完成 Phase 18 场景设计", "priority", "P2", "hours", 7.5));

    state.userBRecordV1 =
        createRecord(
            identities.userB(),
            tokens.userB(),
            state.v1Id,
            state.userBOtherTitle,
            Map.of("summary", "完成其他用户日报", "priority", "P1", "hours", 3));

    assertThat(state.userARecordV1).isNotBlank().isNotEqualTo(state.userBRecordV1);
    assertThat(state.userBRecordV1).isNotBlank();

    JsonNode ownRecords =
        api.getData(
            "/api/work-record/records", tokens.userA(), api.query("page", "1", "pageSize", "20"));

    assertThat(ownRecords.path("total").asLong()).isEqualTo(1);
    assertThat(recordIds(ownRecords)).containsExactly(state.userARecordV1);
  }

  private void assertAdminAuthorization() {
    JsonNode authorization = api.getData("/api/auth/me", tokens.admin());

    assertThat(textValues(authorization.path("roles"))).contains("system_admin");
    assertThat(textValues(authorization.path("permissions"))).contains("work-record:read:all");
    assertThat(authorization.path("dataScopes").path("work-record").asText()).isEqualTo("ALL");
  }

  private void administratorCanReadAllRecords() {
    JsonNode page =
        api.getData(
            "/api/work-record/records", tokens.admin(), api.query("page", "1", "pageSize", "20"));

    assertThat(page.path("total").asLong()).isEqualTo(2);

    assertThat(recordIds(page)).containsExactlyInAnyOrder(state.userARecordV1, state.userBRecordV1);
  }

  private void analyticsWithoutTemplateFilterWorks() {
    ZoneId zone = ZoneId.of("Asia/Shanghai");
    int year = Year.now(zone).getValue();
    MultiValueMap<String, String> query =
        api.query(
            "from", Year.of(year).atDay(1).atStartOfDay(zone).toInstant().toString(),
            "to", Year.of(year + 1).atDay(1).atStartOfDay(zone).toInstant().toString(),
            "groupBy", "day");

    assertThat(
            api.getData("/api/work-record/analytics/statistics", tokens.admin(), query)
                .path("totalRecords")
                .asLong())
        .isEqualTo(2);
    assertThat(
            api.getData("/api/work-record/analytics/workload", tokens.admin(), query)
                .path("users")
                .size())
        .isEqualTo(2);
  }

  private void normalUserCanOnlyReadSelfRecords() {
    JsonNode page =
        api.getData(
            "/api/work-record/records", tokens.userA(), api.query("page", "1", "pageSize", "20"));

    assertThat(recordIds(page)).contains(state.userARecordV1).doesNotContain(state.userBRecordV1);

    ResponseEntity<JsonNode> forbidden =
        api.getRaw("/api/work-record/records/" + state.userBRecordV1, tokens.userA(), api.query());

    assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(forbidden.getBody().path("errorCode").asText()).isEqualTo("FORBIDDEN");
  }

  private void administratorPublishesTemplateV2() {
    api.putData(
        "/api/work-record/templates/" + state.templateId + "/draft",
        tokens.admin(),
        Map.of(
            "name",
            "企业验收日报 v2",
            "description",
            "增加明日计划字段",
            "schemaJson",
            WorkRecordAcceptanceSchemas.v2(state.dictCode),
            "designerJson",
            WorkRecordAcceptanceSchemas.designerV2()));

    JsonNode version =
        api.postData(
            "/api/work-record/templates/" + state.templateId + "/publish",
            tokens.admin(),
            Map.of("versionName", "v2"));

    state.v2Id = version.path("id").asText();
    assertThat(version.path("versionNo").asInt()).isEqualTo(2);
    assertThat(state.v2Id).isNotEqualTo(state.v1Id);
  }

  private void historicalRecordStillUsesV1() {
    JsonNode oldRecord =
        api.getData("/api/work-record/records/" + state.userARecordV1, tokens.admin());

    assertThat(oldRecord.path("templateVersionId").asText()).isEqualTo(state.v1Id);

    JsonNode v1Fields =
        api.getData(
            "/api/work-record/templates/"
                + state.templateId
                + "/versions/"
                + state.v1Id
                + "/fields",
            tokens.admin());

    assertThat(fieldCodes(v1Fields))
        .containsExactlyInAnyOrder("summary", "priority", "hours")
        .doesNotContain("nextPlan");
  }

  private void createNewRecordUsingV2() {
    state.userANewTitle = "用户 A v2 日报 " + state.suffix;

    state.userARecordV2 =
        createRecord(
            identities.userA(),
            tokens.userA(),
            state.v2Id,
            state.userANewTitle,
            Map.of(
                "summary", "完成 v2 工作记录",
                "priority", "P1",
                "hours", 8,
                "nextPlan", "完善企业验收自动化"));

    JsonNode record =
        api.getData("/api/work-record/records/" + state.userARecordV2, tokens.admin());

    assertThat(record.path("templateVersionId").asText()).isEqualTo(state.v2Id);
    assertThat(objectField(record.path("customDataJson").asText(), "nextPlan"))
        .isEqualTo("完善企业验收自动化");
  }

  private void disabledDictionaryItemKeepsHistoricalLabel() {
    api.deleteData(
        "/api/platform/dictionaries/" + state.dictCode + "/items/" + state.p2ItemId,
        tokens.admin());

    JsonNode items =
        api.getData(
            "/api/platform/dictionaries/" + state.dictCode + "/items",
            tokens.admin(),
            api.query("includeDisabled", "true"));

    JsonNode disabled = findById(items, state.p2ItemId);

    assertThat(disabled.path("enabled").asBoolean()).isFalse();
    assertThat(disabled.path("itemLabel").asText()).isEqualTo("中");

    ResponseEntity<byte[]> export =
        export(
            Map.of(
                "templateId", state.templateId,
                "templateVersionId", state.v1Id,
                "keyword", state.userAOldTitle,
                "quickView", "all",
                "columns", List.of("title", "custom.priority")));

    assertThat(export.getStatusCode()).isEqualTo(HttpStatus.OK);

    String csv = new String(export.getBody(), StandardCharsets.UTF_8);

    assertThat(csv).contains(state.userAOldTitle).contains("中（已禁用）");
  }

  private void dynamicFieldFilterWorks() {
    String filters =
        api.toJson(List.of(Map.of("fieldCode", "hours", "operator", "gte", "value", 7)));

    MultiValueMap<String, String> query =
        api.query(
            "page",
            "1",
            "pageSize",
            "20",
            "templateId",
            state.templateId,
            "templateVersionId",
            state.v1Id,
            "dynamicFilters",
            filters);

    JsonNode page = api.getData("/api/work-record/records", tokens.admin(), query);

    assertThat(recordIds(page)).containsExactly(state.userARecordV1);
  }

  private void dynamicFieldsCanBeExported() {
    ResponseEntity<byte[]> export =
        export(
            Map.of(
                "templateId", state.templateId,
                "templateVersionId", state.v2Id,
                "keyword", state.userANewTitle,
                "quickView", "all",
                "columns", List.of("title", "custom.hours", "custom.nextPlan")));

    assertThat(export.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(export.getHeaders().getFirst("X-Export-Row-Count")).isEqualTo("1");

    String csv = new String(export.getBody(), StandardCharsets.UTF_8);

    assertThat(csv)
        .contains(state.userANewTitle)
        .contains("工作时长")
        .contains("明日计划")
        .contains("完善企业验收自动化");
  }

  private void exportLimitIsEnforced() {
    ResponseEntity<JsonNode> rejected =
        api.postCsvError(
            "/api/work-record/records/export",
            tokens.admin(),
            Map.of("quickView", "all", "columns", List.of("title")));

    assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    JsonNode body = rejected.getBody();
    assertThat(body).isNotNull();
    assertThat(body.path("errorCode").asText()).isEqualTo("BAD_REQUEST");
    assertThat(body.path("message").asText()).contains("超过 2 行");
  }

  private void auditTrailIsComplete() {
    JsonNode events = api.getData("/api/audit-logs", tokens.admin());

    assertThat(actionNames(events))
        .contains(
            "platform.dict_type.create",
            "platform.dict_item.create",
            "platform.calendar.create",
            "work_record.template.create",
            "work_record.template.draft.update",
            "work_record.template.publish",
            "work_record.record.create",
            "platform.dict_item.disable",
            "work_record.record.export",
            "work_record.record.export_rejected");

    List<JsonNode> publishes = eventsForAction(events, "work_record.template.publish");

    assertThat(publishes).hasSize(2);

    assertThat(publishes.stream().map(event -> event.path("resourceId").asText()).toList())
        .containsOnly(state.templateId);

    List<JsonNode> creates = eventsForAction(events, "work_record.record.create");

    assertThat(creates).hasSize(3);

    assertThat(creates.stream().map(event -> event.path("resourceId").asText()).toList())
        .containsExactlyInAnyOrder(state.userARecordV1, state.userBRecordV1, state.userARecordV2);

    assertThat(
            creates.stream()
                .map(event -> event.path("actorId").asText())
                .collect(java.util.stream.Collectors.toSet()))
        .contains(identities.userA().id(), identities.userB().id());

    List<JsonNode> successfulExports = eventsForAction(events, "work_record.record.export");

    assertThat(successfulExports).hasSize(2);

    for (JsonNode export : successfulExports) {
      JsonNode detail = parseAuditObject(export, "detailJson");

      assertThat(detail.path("result").asText()).isEqualTo("success");

      assertThat(detail.path("rowCount").asInt()).isEqualTo(1);

      assertThat(detail.path("columns").isArray()).isTrue();

      assertThat(detail.path("query").isObject()).isTrue();
    }

    List<JsonNode> rejectedExports = eventsForAction(events, "work_record.record.export_rejected");

    assertThat(rejectedExports).hasSize(1);

    JsonNode rejectedDetail = parseAuditObject(rejectedExports.getFirst(), "detailJson");

    assertThat(rejectedDetail.path("result").asText()).isEqualTo("limit_exceeded");

    assertThat(rejectedDetail.path("rowCount").asInt()).isEqualTo(3);

    assertThat(rejectedDetail.path("maxRows").asInt()).isEqualTo(2);

    assertThat(rejectedDetail.path("columns").isArray()).isTrue();

    assertThat(rejectedDetail.path("query").isObject()).isTrue();

    List<JsonNode> disabledItems = eventsForAction(events, "platform.dict_item.disable");

    assertThat(disabledItems)
        .singleElement()
        .satisfies(
            event -> assertThat(event.path("resourceId").asText()).isEqualTo(state.p2ItemId));

    assertAuditPayloads(events);
  }

  private String createRecord(
      AcceptanceIdentityFixture.Account account,
      String token,
      String versionId,
      String title,
      Map<String, Object> customData) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("templateId", state.templateId);
    body.put("templateVersionId", versionId);
    body.put("title", title);
    body.put("status", "done");
    body.put("ownerId", account.id());
    body.put("recordTime", OffsetDateTime.now(ZoneOffset.UTC).withNano(0).toString());
    body.put("builtinDataJson", "{}");
    body.put("customDataJson", api.toJson(customData));

    JsonNode record = api.postData("/api/work-record/records", token, body);
    return record.path("id").asText();
  }

  private ResponseEntity<byte[]> export(Map<String, Object> body) {
    return api.postCsv("/api/work-record/records/export", tokens.admin(), body);
  }

  private Set<String> recordIds(JsonNode page) {
    Set<String> result = new LinkedHashSet<>();
    page.path("items").forEach(item -> result.add(item.path("id").asText()));
    return result;
  }

  private Set<String> fieldCodes(JsonNode fields) {
    Set<String> result = new LinkedHashSet<>();
    fields.forEach(field -> result.add(field.path("fieldCode").asText()));
    return result;
  }

  private Set<String> textValues(JsonNode array) {
    Set<String> result = new LinkedHashSet<>();
    array.forEach(value -> result.add(value.asText()));
    return result;
  }

  private JsonNode findById(JsonNode array, String id) {
    for (JsonNode item : array) {
      if (id.equals(item.path("id").asText())) {
        return item;
      }
    }
    throw new AssertionError("item not found: " + id);
  }

  private String objectField(String json, String fieldName) {
    try {
      return objectMapper.readTree(json).path(fieldName).asText();
    } catch (Exception ex) {
      throw new AssertionError("invalid JSON", ex);
    }
  }

  private Set<String> actionNames(JsonNode events) {
    Set<String> result = new LinkedHashSet<>();
    events.forEach(event -> result.add(event.path("action").asText()));
    return result;
  }

  private List<JsonNode> eventsForAction(JsonNode events, String action) {
    List<JsonNode> result = new ArrayList<>();
    events.forEach(
        event -> {
          if (action.equals(event.path("action").asText())) {
            result.add(event);
          }
        });
    return result;
  }

  private void assertAuditPayloads(JsonNode events) {
    for (JsonNode event : events) {
      assertThat(event.path("id").asText()).isNotBlank();

      assertThat(event.path("tenantId").asText()).isEqualTo(identities.tenantId());

      assertThat(event.path("actorId").asText()).isNotBlank();

      assertThat(event.path("action").asText()).isNotBlank();

      assertThat(event.path("resourceType").asText()).isNotBlank();

      assertThat(event.path("resourceId").asText()).isNotBlank();

      assertThat(event.path("createdAt").asText()).isNotBlank();

      assertThat(parseAuditObject(event, "beforeJson").isObject()).isTrue();

      assertThat(parseAuditObject(event, "afterJson").isObject()).isTrue();

      assertThat(parseAuditObject(event, "detailJson").isObject()).isTrue();
    }
  }

  private JsonNode parseAuditObject(JsonNode event, String fieldName) {
    String raw = event.path(fieldName).asText();

    assertThat(raw)
        .as("%s must exist for action %s", fieldName, event.path("action").asText())
        .isNotBlank();

    try {
      JsonNode parsed = objectMapper.readTree(raw);

      assertThat(parsed)
          .as("%s must be a JSON object for action %s", fieldName, event.path("action").asText())
          .isNotNull();

      assertThat(parsed.isObject()).isTrue();

      return parsed;
    } catch (Exception ex) {
      throw new AssertionError(
          "invalid " + fieldName + " for action " + event.path("action").asText(), ex);
    }
  }

  private record Tokens(String admin, String userA, String userB) {}

  private static final class ScenarioState {
    private final String suffix;

    private String dictCode;
    private String p2ItemId;
    private String calendarId;
    private String templateId;
    private String v1Id;
    private String v2Id;
    private String userARecordV1;
    private String userBRecordV1;
    private String userARecordV2;
    private String userAOldTitle;
    private String userBOtherTitle;
    private String userANewTitle;

    private ScenarioState(String suffix) {
      this.suffix = suffix;
    }
  }
}
