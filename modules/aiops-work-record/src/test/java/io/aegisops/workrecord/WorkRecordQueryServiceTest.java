package io.aegisops.workrecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aegisops.common.api.PageResult;
import io.aegisops.security.UserPrincipal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class WorkRecordQueryServiceTest {

  private WorkRecordRepository repository;
  private WorkRecordFieldRepository fieldRepository;
  private WorkRecordQueryService service;

  @BeforeEach
  void setUp() {
    repository = mock(WorkRecordRepository.class);
    fieldRepository = mock(WorkRecordFieldRepository.class);
    service = new WorkRecordQueryService(repository, fieldRepository, new WorkRecordProperties());
  }

  private WorkRecordField textField(String code, boolean filterable, boolean enabled) {
    return new WorkRecordField(
        "f1",
        "t1",
        "tpl1",
        code,
        code,
        "text",
        false,
        null,
        "static",
        null,
        "[]",
        true,
        filterable,
        true,
        false,
        0,
        enabled,
        ".properties." + code,
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }

  private UserPrincipal makeUser(Collection<? extends GrantedAuthority> authorities) {
    UserPrincipal user = mock(UserPrincipal.class);
    when(user.id()).thenReturn("user-1");
    when(user.getAuthorities()).thenAnswer(inv -> new ArrayList<>(authorities));
    return user;
  }

  private static List<GrantedAuthority> auth(String... names) {
    List<GrantedAuthority> out = new ArrayList<>();
    for (String n : names) {
      out.add(new SimpleGrantedAuthority(n));
    }
    return out;
  }

  private WorkRecordListRequest emptyRequest() {
    List<DynamicFieldFilter> noFilters = List.of();
    return new WorkRecordListRequest(
        1, 20, null, null, null, null, null, null, null, noFilters, null);
  }

  private WorkRecordListRequest requestWithFilters(List<DynamicFieldFilter> filters) {
    return new WorkRecordListRequest(
        1, 20, "tpl1", null, null, null, null, null, null, filters, null);
  }

  @Nested
  class ListTests {

    @Test
    void adminUser_callsRepositoryPage() {
      UserPrincipal user = makeUser(auth("work-record:read:all"));
      when(repository.page(anyString(), anyInt(), anyInt(), any(), any(), any(), any(), any(), any(), any(), any()))
          .thenReturn(new PageResult<>(0, 1, 20, List.of()));

      var result = service.list("t1", user, emptyRequest());

      assertThat(result).isNotNull();
      verify(repository).page(eq("t1"), eq(1), eq(20), any(), any(), any(), any(), any(), any(), any(), any());
      verify(repository, never()).pageForUser(anyString(), anyString(), anyInt(), anyInt(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void normalUser_callsRepositoryPageForUser() {
      UserPrincipal user = makeUser(auth("work-record:read:self"));
      when(repository.pageForUser(anyString(), anyString(), anyInt(), anyInt(), any(), any(), any(), any(), any(), any()))
          .thenReturn(new PageResult<>(0, 1, 20, List.of()));

      var result = service.list("t1", user, emptyRequest());

      assertThat(result).isNotNull();
      verify(repository).pageForUser(eq("t1"), eq("user-1"), eq(1), eq(20), any(), any(), any(), any(), any(), any());
    }

    @Test
    void pageSizeClampedToMax100() {
      UserPrincipal user = makeUser(auth("work-record:read:all"));
      List<DynamicFieldFilter> noFilters = List.of();
      WorkRecordListRequest req = new WorkRecordListRequest(
          1, 500, null, null, null, null, null, null, null, noFilters, null);
      when(repository.page(anyString(), anyInt(), anyInt(), any(), any(), any(), any(), any(), any(), any(), any()))
          .thenReturn(new PageResult<>(0, 1, 100, List.of()));

      service.list("t1", user, req);

      verify(repository).page(eq("t1"), eq(1), eq(100), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void dynamicFilters_requiresTemplateId() {
      UserPrincipal user = makeUser(auth("work-record:read:all"));
      List<DynamicFieldFilter> filters = List.of(new DynamicFieldFilter("memo", "contains", "hello", null));
      WorkRecordListRequest req = new WorkRecordListRequest(
          1, 20, null, null, null, null, null, null, null, filters, null);

      assertThatThrownBy(() -> service.list("t1", user, req))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("templateId is required");
    }

    @Test
    void dynamicFilters_validatedAgainstTemplate() {
      UserPrincipal user = makeUser(auth("work-record:read:all"));
      WorkRecordField field = textField("memo", true, true);
      when(fieldRepository.list("t1", "tpl1")).thenReturn(List.of(field));
      when(repository.page(anyString(), anyInt(), anyInt(), any(), any(), any(), any(), any(), any(), any(), any()))
          .thenReturn(new PageResult<>(0, 1, 20, List.of()));

      WorkRecordListRequest req = requestWithFilters(
          List.of(new DynamicFieldFilter("unknown", "contains", "x", null)));

      assertThatThrownBy(() -> service.list("t1", user, req))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("unknown");
    }

    @Test
    void dynamicFilters_valid_succeeds() {
      UserPrincipal user = makeUser(auth("work-record:read:all"));
      WorkRecordField field = textField("memo", true, true);
      when(fieldRepository.list("t1", "tpl1")).thenReturn(List.of(field));
      when(repository.page(anyString(), anyInt(), anyInt(), any(), any(), any(), any(), any(), any(), any(), any()))
          .thenReturn(new PageResult<>(0, 1, 20, List.of()));

      WorkRecordListRequest req = requestWithFilters(
          List.of(new DynamicFieldFilter("memo", "contains", "hello", null)));

      var result = service.list("t1", user, req);
      assertThat(result).isNotNull();
    }
  }

  @Nested
  class MetadataTests {

    @Test
    void emptyTemplateId_returnsTemplatesOnly() {
      WorkRecordTemplate t1 = new WorkRecordTemplate(
          "tpl1", "t1", "T1", "t1", null, true, "{}", "{}", null,
          OffsetDateTime.now(), OffsetDateTime.now());
      WorkRecordTemplate t2 = new WorkRecordTemplate(
          "tpl2", "t1", "T2", "t2", null, true, "{}", "{}", null,
          OffsetDateTime.now(), OffsetDateTime.now());
      when(repository.listTemplates("t1")).thenReturn(List.of(t1, t2));

      var meta = service.metadata("t1", null);

      assertThat(meta.templates()).hasSize(2);
      assertThat(meta.columns()).isEmpty();
      assertThat(meta.filterFields()).isEmpty();
    }

    @Test
    void withTemplateId_returnsColumnsAndFilterFields() {
      WorkRecordField text = textField("memo", true, true);
      WorkRecordField number = new WorkRecordField(
          "f2", "t1", "tpl1", "count", "count", "number",
          false, null, "static", null, "[]",
          true, true, true, false, 0, true,
          ".properties.count", OffsetDateTime.now(), OffsetDateTime.now());
      when(fieldRepository.list("t1", "tpl1")).thenReturn(List.of(text, number));

      var meta = service.metadata("t1", "tpl1");

      assertThat(meta.columns()).hasSize(2);
      assertThat(meta.filterFields()).hasSize(2);
      WorkRecordQueryService.RecordListFilterField memo = meta.filterFields().stream()
          .filter(f -> f.fieldCode().equals("memo"))
          .findFirst()
          .orElseThrow();
      assertThat(memo.operators()).contains("contains", "eq", "exists");
      assertThat(memo.exportable()).isTrue();
      assertThat(meta.columns().get(0).exportable()).isTrue();
    }

    @Test
    void disabledFields_excluded() {
      WorkRecordField enabled = textField("memo", true, true);
      WorkRecordField disabled = textField("archived", true, false);
      when(fieldRepository.list("t1", "tpl1")).thenReturn(List.of(enabled, disabled));

      var meta = service.metadata("t1", "tpl1");

      assertThat(meta.columns()).extracting(WorkRecordQueryService.RecordListColumn::fieldCode)
          .containsExactly("memo");
    }

    @Test
    void nonFilterableFields_appearInColumnsButNotFilters() {
      WorkRecordField filterable = textField("memo", true, true);
      WorkRecordField nonFilterable = textField("note", false, true);
      when(fieldRepository.list("t1", "tpl1")).thenReturn(List.of(filterable, nonFilterable));

      var meta = service.metadata("t1", "tpl1");

      assertThat(meta.columns()).hasSize(2);
      assertThat(meta.filterFields()).hasSize(1);
      assertThat(meta.filterFields().get(0).fieldCode()).isEqualTo("memo");
    }
  }
}
