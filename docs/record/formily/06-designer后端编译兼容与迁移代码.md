---
title: Designer 后端编译、旧版本兼容与迁移代码
type: design
status: draft
phase: work-record
owner: ai
created: 2026-07-14
updated: 2026-07-14
related: []
---

# Designer 后端编译、旧版本兼容与迁移代码

## 1. 后端不信任前端 Schema

前端提交：

```json
{
  "draftDesignerJson": "...",
  "draftSchemaJson": "..."
}
```

后端处理：

```text
解析 DesignerDocument
校验节点与字段
后端编译 normalizedSchema
比较客户端 normalizedSchema
保存后端编译结果
同步字段索引
```

## 2. Java Domain

```java
public record DesignerDocument(
    int version,
    String templateId,
    List<DesignerNode> nodes,
    DesignerSettings settings) {}

public sealed interface DesignerNode
    permits DesignerFieldNode, DesignerSectionNode,
            DesignerGridNode, DesignerDividerNode {
  String id();
}

public record DesignerFieldNode(
    String id,
    String fieldCode,
    String fieldName,
    FieldType fieldType,
    String description,
    boolean required,
    boolean enabled,
    boolean locked,
    boolean referenced,
    JsonNode componentProps,
    List<FieldValidatorRule> validators,
    FieldDataSource dataSource,
    FieldCapability capability)
    implements DesignerNode {}
```

## 3. Parser

```java
@Component
public class DesignerDocumentParser {

  private final ObjectMapper objectMapper;

  public DesignerDocumentParser(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public DesignerDocument parse(String json) {
    if (json == null || json.isBlank()) {
      throw new AppException(
          ErrorCode.DESIGNER_DOCUMENT_INVALID,
          "designer document is required");
    }

    try {
      JsonNode root = objectMapper.readTree(json);
      int version = root.path("version").asInt(1);

      return switch (version) {
        case 1 -> parseLegacy(root);
        case 2 -> objectMapper.treeToValue(root, DesignerDocument.class);
        default -> throw new AppException(
            ErrorCode.DESIGNER_DOCUMENT_INVALID,
            "unsupported designer document version: " + version);
      };
    } catch (AppException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new AppException(
          ErrorCode.DESIGNER_DOCUMENT_INVALID,
          "designer document is malformed",
          ex);
    }
  }

  private DesignerDocument parseLegacy(JsonNode root) {
    List<DesignerNode> nodes = new ArrayList<>();
    int index = 0;

    for (JsonNode field : root.path("fields")) {
      nodes.add(new DesignerFieldNode(
          field.path("id").asText(UUID.randomUUID().toString()),
          field.path("fieldCode").asText(),
          field.path("fieldName").asText(),
          FieldType.from(field.path("fieldType").asText()),
          null,
          field.path("required").asBoolean(false),
          field.path("enabled").asBoolean(true),
          field.path("locked").asBoolean(false),
          field.path("referenced").asBoolean(false),
          objectMapper.createObjectNode(),
          List.of(),
          legacyDataSource(field),
          legacyCapability(field)));
      index++;
    }

    return new DesignerDocument(
        2,
        root.path("templateId").asText(""),
        nodes,
        new DesignerSettings("md", "top", null));
  }
}
```

## 4. Validator

```java
@Component
public class DesignerDocumentValidator {

  private static final Pattern FIELD_CODE =
      Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]{0,63}$");

  private final DictionaryPort dictionaryPort;

  public void validate(String tenantId, DesignerDocument document) {
    if (document.version() != 2) {
      fail("designer document version must be 2");
    }

    if (document.nodes().size() > 500) {
      fail("designer document exceeds 500 root nodes");
    }

    Set<String> nodeIds = new HashSet<>();
    Set<String> fieldCodes = new HashSet<>();

    walk(document.nodes(), node -> {
      if (!nodeIds.add(node.id())) {
        fail("duplicated designer node id: " + node.id());
      }

      if (node instanceof DesignerFieldNode field) {
        FieldCodeRules.validate(field.fieldCode());

        if (!fieldCodes.add(field.fieldCode())) {
          fail("duplicated fieldCode: " + field.fieldCode());
        }

        validateDataSource(tenantId, field);
        validateComponentProps(field);
      }
    });
  }

  private void validateDataSource(
      String tenantId,
      DesignerFieldNode field) {
    if (field.dataSource() instanceof DictFieldDataSource dict) {
      if (!dictionaryPort.exists(tenantId, dict.dictCode())) {
        fail("dictionary not found: " + dict.dictCode());
      }
    }
  }

  private void fail(String message) {
    throw new AppException(
        ErrorCode.DESIGNER_DOCUMENT_INVALID,
        message);
  }
}
```

## 5. Compiler

```java
public interface WorkRecordDesignerCompiler {
  CompiledDesignerDocument compile(DesignerDocument document);
}

public record CompiledDesignerDocument(
    String normalizedDesignerJson,
    String normalizedSchemaJson,
    String fieldIndexJson,
    List<WorkRecordFieldDefinition> fields) {}
```

```java
@Component
public class JacksonWorkRecordDesignerCompiler
    implements WorkRecordDesignerCompiler {

  private final ObjectMapper objectMapper;
  private final DesignerDocumentValidator validator;

  @Override
  public CompiledDesignerDocument compile(
      DesignerDocument document) {
    ObjectNode schema = objectMapper.createObjectNode();
    schema.put("type", "object");
    schema.put("x-work-record-schema-version", 2);

    ObjectNode properties = schema.putObject("properties");
    List<WorkRecordFieldDefinition> fields = new ArrayList<>();

    compileNodes(document.nodes(), properties, fields, new AtomicInteger());

    return new CompiledDesignerDocument(
        canonicalJson(document),
        canonicalJson(schema),
        canonicalJson(buildFieldIndex(fields)),
        List.copyOf(fields));
  }

  private void compileNodes(
      List<DesignerNode> nodes,
      ObjectNode properties,
      List<WorkRecordFieldDefinition> fields,
      AtomicInteger order) {
    for (DesignerNode node : nodes) {
      if (node instanceof DesignerFieldNode field) {
        if (!field.enabled()) {
          continue;
        }

        properties.set(field.fieldCode(), compileField(field, order.getAndIncrement()));
        fields.add(toFieldDefinition(field, order.get()));
        continue;
      }

      String key = "__layout_" + node.id();
      properties.set(key, compileLayout(node, fields, order));
    }
  }
}
```

编译映射必须与前端共享契约样本。不要复制一份无测试的不同逻辑。

## 6. 保存草稿接入

```java
@Transactional
public WorkRecordTemplate saveDraft(
    String tenantId,
    String templateId,
    SaveTemplateDraftCommand command,
    UserPrincipal actor) {
  permissionService.requireTemplateWrite(actor);

  payloadPolicy.requireDesigner(command.designerJson());

  DesignerDocument document =
      designerDocumentParser.parse(command.designerJson());

  designerValidator.validate(tenantId, document);

  CompiledDesignerDocument compiled =
      designerCompiler.compile(document);

  payloadPolicy.requireSchema(compiled.normalizedSchemaJson());
  payloadPolicy.requireDesigner(compiled.normalizedDesignerJson());
  payloadPolicy.requireFieldIndex(compiled.fieldIndexJson());

  if (command.schemaJson() != null
      && !schemaComparator.equivalent(
          command.schemaJson(),
          compiled.normalizedSchemaJson())) {
    throw new AppException(
        ErrorCode.DESIGNER_SCHEMA_MISMATCH,
        "client schema does not match designer document");
  }

  return repository.updateDraft(
      tenantId,
      templateId,
      compiled.normalizedSchemaJson(),
      compiled.normalizedDesignerJson(),
      actor.id());
}
```

## 7. 发布

发布继续沿用现有模板版本流程，但字段索引使用 compiler 输出，不能再次从客户端 JSON 猜测。

发布前比较当前版本：

```text
新增字段
禁用字段
字段类型变化
字典变化
列表/筛选/导出能力变化
```

禁止：

```text
已引用字段 fieldCode 变化
不兼容 fieldType 变化
历史字段物理删除
```

## 8. 兼容旧 Designer JSON

读取模板草稿时：

```java
public DesignerDocument getDraftDesigner(...) {
  String raw = repository.requireTemplate(...).draftDesignerJson();
  return designerDocumentParser.parse(raw);
}
```

旧格式自动转换为 v2 返回给前端。第一次保存时写回 v2。

历史 `wr_template_version.designer_json` 不批量更新；详情运行态只依赖 version.schema_json。

## 9. 错误码

```java
DESIGNER_DOCUMENT_INVALID(422),
DESIGNER_NODE_DUPLICATED(422),
DESIGNER_FIELD_CODE_DUPLICATED(422),
DESIGNER_SCHEMA_MISMATCH(422),
DESIGNER_LAYOUT_INVALID(422),
DESIGNER_FIELD_INCOMPATIBLE(409)
```

## 10. Contract Fixtures

仓库增加：

```text
contracts/work-record-designer/
├── simple-v2.json
├── nested-layout-v2.json
├── dictionary-field-v2.json
├── legacy-v1.json
└── expected-schema/
```

前端 Vitest 和后端 JUnit 都读取同一批 JSON Fixtures，断言规范化结果一致。

## 11. Java 单元测试

```java
class DesignerDocumentParserTest {

  @Test
  void upgradesLegacyFlatFieldsToVersionTwo() {
    DesignerDocument document =
        parser.parse(readFixture("legacy-v1.json"));

    assertThat(document.version()).isEqualTo(2);
    assertThat(document.nodes())
        .filteredOn(DesignerFieldNode.class::isInstance)
        .hasSize(3);
  }

  @Test
  void rejectsDuplicatedFieldCodes() {
    DesignerDocument document =
        fixtureWithFields("summary", "summary");

    assertThatThrownBy(() ->
        validator.validate("tenant-1", document))
        .isInstanceOf(AppException.class)
        .extracting(ex -> ((AppException) ex).errorCode())
        .isEqualTo("DESIGNER_FIELD_CODE_DUPLICATED");
  }
}
```

```java
class DesignerCompilerContractTest {

  @ParameterizedTest
  @ValueSource(strings = {
      "simple-v2",
      "nested-layout-v2",
      "dictionary-field-v2",
      "legacy-v1"
  })
  void producesExpectedCanonicalSchema(String fixture) {
    DesignerDocument document =
        parser.parse(readFixture(fixture + ".json"));

    CompiledDesignerDocument compiled =
        compiler.compile(document);

    assertThatJson(compiled.normalizedSchemaJson())
        .isEqualTo(readExpectedSchema(fixture));
  }
}
```

## 12. PostgreSQL IT

验证：

```text
保存 legacy 草稿后写回 v2
发布 v1 后字段锁定
存在历史记录时禁用字段
发布 v2
v1 记录仍绑定 v1
v2 新记录使用 v2
并发保存 rowVersion 冲突
```
