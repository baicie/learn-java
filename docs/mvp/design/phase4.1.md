# Phase4.1：Agent Context Contract & Safety Boundary

默认前提：**Phase4.0 已经完成**，也就是现在已经有：

```txt id="wtvmer"
apps/aiops-agent
  FastAPI + LangGraph OSS
  deterministic diagnosis graph

modules/aiops-ai-client
  Java 调 Python Agent
  保存 ai_diagnosis
  写 incident_timeline
```

Phase4.1 不新增业务能力，核心是把 **Java ↔ Python Agent 的协议、版本、校验、安全边界** 固化下来，防止后续 Phase4.2 接 LLM、Phase4.3 接工具、Phase5 接执行器后失控。

---

# 1. Phase4.1 目标

## 1.1 本阶段完成

```txt id="xxejnh"
1. 新增统一 contracts/agent JSON Schema
2. AgentDiagnosisRequest 增加 contractVersion
3. AgentDiagnosisResponse 增加 contractVersion
4. Java 调 Python 前校验 request contract
5. Java 接收 Python 后校验 response contract
6. Python FastAPI 入参继续由 Pydantic 校验
7. Python 输出前执行 contract + safety 校验
8. Python 增加 /v1/contracts/diagnosis endpoint
9. internal token + traceId + contractVersion 全链路透传
10. 禁止 Agent 返回高危自动执行建议
11. 完整 Java 单元测试
12. 完整 Python 单元测试
```

## 1.2 本阶段不做

```txt id="eknvpc"
1. 不接真实 LLM
2. 不做 streaming
3. 不做 LangGraph checkpoint
4. 不接指标 / 日志 / 变更真实查询
5. 不执行 Runbook
6. 不执行 Ansible / SSH / Webhook
7. 不改 ai_diagnosis 表
8. 不改前端展示
```

---

# 2. Phase4.1 后的数据流

```txt id="py9o1i"
web/console
  ↓
aiops-server Java
  ↓
AiDiagnosisService
  ↓
AgentDiagnosisRequest(contractVersion=agent-diagnosis.v1)
  ↓
AgentContractValidator.validateRequest()
  ↓
HttpAiAgentClient
  - X-AegisOps-Internal-Token
  - X-AegisOps-Trace-Id
  - X-AegisOps-Contract-Version
  ↓
apps/aiops-agent FastAPI
  ↓
Pydantic request validation
  ↓
LangGraph diagnosis graph
  ↓
SafetyBoundary.apply()
  ↓
Agent response contract validation
  ↓
Java AgentContractValidator.validateResponse()
  ↓
ai_diagnosis 落库
  ↓
incident_timeline 写入 ai_diagnosed
```

---

# 3. 新增 / 修改文件清单

```txt id="6ui7bo"
contracts/
  agent/
    diagnosis-request.schema.json
    diagnosis-response.schema.json
    examples/
      diagnosis-request.example.json
      diagnosis-response.example.json

modules/aiops-ai-client/
  src/main/java/io/aegisops/ai/client/
    AgentContract.java
    AgentContractValidator.java
    AgentContractViolationException.java
    HttpAiAgentClient.java              # 替换
    AiDiagnosisService.java             # 替换
  src/main/java/io/aegisops/ai/client/dto/
    AgentDiagnosisRequest.java          # 替换
    AgentDiagnosisResponse.java         # 替换
  src/test/java/io/aegisops/ai/client/
    AgentContractValidatorTest.java     # 新增
    HttpAiAgentClientTest.java          # 替换
    AiDiagnosisServiceTest.java         # 替换

apps/aiops-agent/
  src/aiops_agent/
    settings.py                         # 替换
    schemas.py                          # 替换
    contract.py                         # 新增
    safety.py                           # 新增
    graph.py                            # 替换
    main.py                             # 替换
  tests/
    test_contract.py                    # 新增
    test_safety.py                      # 新增
    test_graph.py                       # 替换
    test_api.py                         # 替换
```

---

# 4. Contract Schema

## 4.1 `contracts/agent/diagnosis-request.schema.json`

```json id="o1ss6e"
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://aegisops.local/contracts/agent/diagnosis-request.schema.json",
  "title": "AegisOps Agent Diagnosis Request",
  "type": "object",
  "additionalProperties": false,
  "required": [
    "contractVersion",
    "tenantId",
    "incidentId",
    "incident",
    "alerts",
    "locale",
    "traceId"
  ],
  "properties": {
    "contractVersion": {
      "type": "string",
      "const": "agent-diagnosis.v1"
    },
    "tenantId": {
      "type": "string",
      "minLength": 1
    },
    "incidentId": {
      "type": "string",
      "minLength": 1
    },
    "incident": {
      "type": "object",
      "required": ["id"],
      "additionalProperties": true,
      "properties": {
        "id": { "type": "string", "minLength": 1 },
        "title": { "type": ["string", "null"] },
        "summary": { "type": ["string", "null"] },
        "severity": { "type": ["string", "null"] },
        "status": { "type": ["string", "null"] },
        "source": { "type": ["string", "null"] },
        "primaryAssetId": { "type": ["string", "null"] },
        "aggregationKey": { "type": ["string", "null"] },
        "alertCount": { "type": "integer", "minimum": 0 },
        "suspectedRootCause": { "type": ["string", "null"] },
        "confidence": { "type": ["number", "null"] },
        "startedAt": { "type": ["string", "null"] },
        "detectedAt": { "type": ["string", "null"] },
        "lastSeenAt": { "type": ["string", "null"] }
      }
    },
    "alerts": {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["id"],
        "additionalProperties": true,
        "properties": {
          "id": { "type": "string", "minLength": 1 },
          "source": { "type": ["string", "null"] },
          "sourceEventId": { "type": ["string", "null"] },
          "severity": { "type": ["string", "null"] },
          "title": { "type": ["string", "null"] },
          "description": { "type": ["string", "null"] },
          "assetId": { "type": ["string", "null"] },
          "entityType": { "type": ["string", "null"] },
          "entityName": { "type": ["string", "null"] },
          "fingerprint": { "type": ["string", "null"] },
          "labelsJson": { "type": ["string", "null"] },
          "startsAt": { "type": ["string", "null"] }
        }
      }
    },
    "rca": {
      "type": ["object", "null"],
      "additionalProperties": true,
      "properties": {
        "id": { "type": "string" },
        "suspectedRootCause": { "type": ["string", "null"] },
        "confidence": { "type": ["number", "null"] },
        "summary": { "type": ["string", "null"] },
        "evidenceJson": { "type": ["string", "null"] },
        "modelVersion": { "type": ["string", "null"] },
        "createdAt": { "type": ["string", "null"] }
      }
    },
    "locale": {
      "type": "string",
      "minLength": 1
    },
    "traceId": {
      "type": "string",
      "minLength": 1
    }
  }
}
```

---

## 4.2 `contracts/agent/diagnosis-response.schema.json`

```json id="b7xg3f"
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://aegisops.local/contracts/agent/diagnosis-response.schema.json",
  "title": "AegisOps Agent Diagnosis Response",
  "type": "object",
  "additionalProperties": false,
  "required": [
    "contractVersion",
    "provider",
    "model",
    "agentName",
    "summary",
    "rootCause",
    "impact",
    "nextSteps",
    "runbookSuggestions",
    "risks",
    "raw"
  ],
  "properties": {
    "contractVersion": {
      "type": "string",
      "const": "agent-diagnosis.v1"
    },
    "provider": {
      "type": "string",
      "minLength": 1
    },
    "model": {
      "type": "string",
      "minLength": 1
    },
    "agentName": {
      "type": "string",
      "minLength": 1
    },
    "summary": {
      "type": "string",
      "minLength": 1
    },
    "rootCause": {
      "type": "string",
      "minLength": 1
    },
    "impact": {
      "type": "string",
      "minLength": 1
    },
    "nextSteps": {
      "type": "array",
      "items": { "type": "string" }
    },
    "runbookSuggestions": {
      "type": "array",
      "items": { "type": "string" }
    },
    "risks": {
      "type": "array",
      "items": { "type": "string" }
    },
    "raw": {
      "type": "object"
    }
  }
}
```

---

## 4.3 `contracts/agent/examples/diagnosis-request.example.json`

```json id="b74wni"
{
  "contractVersion": "agent-diagnosis.v1",
  "tenantId": "tenant_1",
  "incidentId": "inc_1",
  "incident": {
    "id": "inc_1",
    "title": "CPU high",
    "summary": "CPU usage is high on host-1",
    "severity": "critical",
    "status": "open",
    "source": "zabbix",
    "primaryAssetId": "asset_1",
    "aggregationKey": "zabbix:fp_cpu",
    "alertCount": 2,
    "suspectedRootCause": "CPU saturation",
    "confidence": 0.8,
    "startedAt": "2026-06-14T10:00:00+09:00",
    "detectedAt": "2026-06-14T10:01:00+09:00",
    "lastSeenAt": "2026-06-14T10:05:00+09:00"
  },
  "alerts": [
    {
      "id": "alert_1",
      "source": "zabbix",
      "sourceEventId": "event_1",
      "severity": "critical",
      "title": "CPU high",
      "description": "CPU usage is above threshold",
      "assetId": "asset_1",
      "entityType": "host",
      "entityName": "host-1",
      "fingerprint": "fp_cpu",
      "labelsJson": "{}",
      "startsAt": "2026-06-14T10:00:00+09:00"
    }
  ],
  "rca": {
    "id": "rca_1",
    "suspectedRootCause": "CPU saturation",
    "confidence": 0.8,
    "summary": "High severity CPU alerts concentrated on one host.",
    "evidenceJson": "[]",
    "modelVersion": "rules-v1",
    "createdAt": "2026-06-14T10:06:00+09:00"
  },
  "locale": "zh-CN",
  "traceId": "trace_1"
}
```

---

## 4.4 `contracts/agent/examples/diagnosis-response.example.json`

```json id="xytigy"
{
  "contractVersion": "agent-diagnosis.v1",
  "provider": "aiops-agent",
  "model": "langgraph-deterministic",
  "agentName": "aegisops_diagnosis_graph",
  "summary": "Incident inc_1 is open with severity critical. 1 linked alert(s) were analyzed.",
  "rootCause": "CPU saturation",
  "impact": "The primary impact may be concentrated on asset_1.",
  "nextSteps": [
    "Confirm whether the dominant fingerprint `fp_cpu` is still firing.",
    "Check the primary asset `asset_1` around the incident start time."
  ],
  "runbookSuggestions": ["Host resource saturation investigation"],
  "risks": ["Do not execute remediation automatically in Phase4.1."],
  "raw": {
    "graph": "aegisops_diagnosis_graph",
    "generationMode": "deterministic"
  }
}
```

---

# 5. Java 代码

## 5.1 `AgentContract.java`

```java id="pz6t0n"
package io.aegisops.ai.client;

public final class AgentContract {
    private AgentContract() {}

    public static final String DIAGNOSIS_CONTRACT_VERSION = "agent-diagnosis.v1";
    public static final String CONTRACT_VERSION_HEADER = "X-AegisOps-Contract-Version";
    public static final String TRACE_ID_HEADER = "X-AegisOps-Trace-Id";
    public static final String INTERNAL_TOKEN_HEADER = "X-AegisOps-Internal-Token";
}
```

---

## 5.2 `AgentContractViolationException.java`

```java id="6auuix"
package io.aegisops.ai.client;

import io.aegisops.common.exception.AppException;

public class AgentContractViolationException extends AppException {
    public AgentContractViolationException(String message) {
        super("AI_AGENT_CONTRACT_VIOLATION", message);
    }
}
```

---

## 5.3 `dto/AgentDiagnosisRequest.java`

```java id="6wtedw"
package io.aegisops.ai.client.dto;

import java.util.List;

public record AgentDiagnosisRequest(
        String contractVersion,
        String tenantId,
        String incidentId,
        AgentIncidentContext incident,
        List<AgentAlertContext> alerts,
        AgentRcaContext rca,
        String locale,
        String traceId
) {}
```

---

## 5.4 `dto/AgentDiagnosisResponse.java`

```java id="jm6eae"
package io.aegisops.ai.client.dto;

import java.util.List;
import java.util.Map;

public record AgentDiagnosisResponse(
        String contractVersion,
        String provider,
        String model,
        String agentName,
        String summary,
        String rootCause,
        String impact,
        List<String> nextSteps,
        List<String> runbookSuggestions,
        List<String> risks,
        Map<String, Object> raw
) {}
```

---

## 5.5 `AgentContractValidator.java`

```java id="53rk97"
package io.aegisops.ai.client;

import io.aegisops.ai.client.dto.AgentDiagnosisRequest;
import io.aegisops.ai.client.dto.AgentDiagnosisResponse;

import java.util.Collection;
import java.util.Map;

public class AgentContractValidator {
    public void validateRequest(AgentDiagnosisRequest request) {
        if (request == null) {
            throw violation("request is null");
        }

        requireEquals("contractVersion", AgentContract.DIAGNOSIS_CONTRACT_VERSION, request.contractVersion());
        requireText("tenantId", request.tenantId());
        requireText("incidentId", request.incidentId());
        requireText("locale", request.locale());
        requireText("traceId", request.traceId());

        if (request.incident() == null) {
            throw violation("incident is required");
        }

        requireText("incident.id", request.incident().id());

        if (request.incident().alertCount() < 0) {
            throw violation("incident.alertCount must be >= 0");
        }

        if (request.alerts() == null) {
            throw violation("alerts is required");
        }

        for (int i = 0; i < request.alerts().size(); i++) {
            if (request.alerts().get(i) == null) {
                throw violation("alerts[" + i + "] is null");
            }
            requireText("alerts[" + i + "].id", request.alerts().get(i).id());
        }

        if (request.rca() != null) {
            requireText("rca.id", request.rca().id());
        }
    }

    public void validateResponse(AgentDiagnosisResponse response) {
        if (response == null) {
            throw violation("response is null");
        }

        requireEquals("contractVersion", AgentContract.DIAGNOSIS_CONTRACT_VERSION, response.contractVersion());
        requireText("provider", response.provider());
        requireText("model", response.model());
        requireText("agentName", response.agentName());
        requireText("summary", response.summary());
        requireText("rootCause", response.rootCause());
        requireText("impact", response.impact());
        requireList("nextSteps", response.nextSteps());
        requireList("runbookSuggestions", response.runbookSuggestions());
        requireList("risks", response.risks());

        if (response.raw() == null) {
            throw violation("raw is required");
        }

        validateNoUnsafeAutoExecution(response);
    }

    private void validateNoUnsafeAutoExecution(AgentDiagnosisResponse response) {
        String joined = String.join("\n",
                response.nextSteps(),
                response.runbookSuggestions(),
                response.risks()
        ).toLowerCase();

        String[] forbidden = {
                "rm -rf",
                "drop database",
                "truncate table",
                "delete namespace",
                "kubectl delete",
                "format disk",
                "shutdown -h",
                "reboot now",
                "无需审批自动",
                "自动执行删除",
                "自动清空"
        };

        for (String keyword : forbidden) {
            if (joined.contains(keyword)) {
                throw violation("response contains forbidden unsafe action keyword: " + keyword);
            }
        }
    }

    private void requireText(String field, String value) {
        if (value == null || value.isBlank()) {
            throw violation(field + " is required");
        }
    }

    private void requireList(String field, Collection<String> value) {
        if (value == null) {
            throw violation(field + " is required");
        }

        for (String item : value) {
            if (item == null) {
                throw violation(field + " contains null item");
            }
        }
    }

    private void requireEquals(String field, String expected, String actual) {
        if (!expected.equals(actual)) {
            throw violation(field + " must be " + expected);
        }
    }

    private AgentContractViolationException violation(String message) {
        return new AgentContractViolationException(message);
    }
}
```

---

## 5.6 `HttpAiAgentClient.java`

```java id="9u98i6"
package io.aegisops.ai.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.ai.client.dto.AgentDiagnosisRequest;
import io.aegisops.ai.client.dto.AgentDiagnosisResponse;
import io.aegisops.common.exception.AppException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@EnableConfigurationProperties(AgentClientProperties.class)
public class HttpAiAgentClient implements AiAgentClient {
    private final AgentClientProperties properties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final AgentContractValidator contractValidator;

    public HttpAiAgentClient(AgentClientProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, createRestTemplate(properties), new AgentContractValidator());
    }

    HttpAiAgentClient(
            AgentClientProperties properties,
            ObjectMapper objectMapper,
            RestTemplate restTemplate,
            AgentContractValidator contractValidator
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
        this.contractValidator = contractValidator;
    }

    @Override
    public AgentDiagnosisResponse diagnose(AgentDiagnosisRequest request) {
        try {
            contractValidator.validateRequest(request);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(AgentContract.INTERNAL_TOKEN_HEADER, properties.normalizedInternalToken());
            headers.set(AgentContract.TRACE_ID_HEADER, request.traceId());
            headers.set(AgentContract.CONTRACT_VERSION_HEADER, AgentContract.DIAGNOSIS_CONTRACT_VERSION);

            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(request), headers);

            ResponseEntity<AgentDiagnosisResponse> response = restTemplate.exchange(
                    properties.normalizedBaseUrl() + "/v1/diagnose",
                    HttpMethod.POST,
                    entity,
                    AgentDiagnosisResponse.class
            );

            AgentDiagnosisResponse body = response.getBody();
            contractValidator.validateResponse(body);

            return body;
        } catch (AgentContractViolationException ex) {
            throw ex;
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AppException("AI_AGENT_CALL_FAILED", "Failed to call AI diagnosis agent");
        }
    }

    private static RestTemplate createRestTemplate(AgentClientProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.normalizedConnectTimeoutMillis());
        factory.setReadTimeout(properties.normalizedReadTimeoutMillis());
        return new RestTemplate(factory);
    }
}
```

---

## 5.7 `AiDiagnosisService.java`

```java id="dn3nz0"
package io.aegisops.ai.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.ai.client.dto.*;
import io.aegisops.common.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AiDiagnosisService {
    private final AiRepository repository;
    private final AiAgentClient agentClient;
    private final ObjectMapper objectMapper;
    private final AgentContractValidator contractValidator;

    public AiDiagnosisService(AiRepository repository, AiAgentClient agentClient, ObjectMapper objectMapper) {
        this.repository = repository;
        this.agentClient = agentClient;
        this.objectMapper = objectMapper;
        this.contractValidator = new AgentContractValidator();
    }

    public AiDiagnosisResponse latest(String tenantId, String incidentId) {
        ensureIncidentExists(tenantId, incidentId);
        return repository.findLatestDiagnosis(tenantId, incidentId)
                .map(this::toResponse)
                .orElseThrow(() -> new AppException("AI_DIAGNOSIS_NOT_FOUND", "AI diagnosis not found"));
    }

    @Transactional
    public AiDiagnosisResponse diagnose(String tenantId, String incidentId, AiDiagnoseRequest request) {
        AiDiagnoseRequest normalized = request == null ? new AiDiagnoseRequest(false, "zh-CN") : request;

        AiIncidentRecord incident = repository.findIncident(tenantId, incidentId)
                .orElseThrow(() -> new AppException("INCIDENT_NOT_FOUND", "Incident not found"));

        if (!normalized.forceEnabled()) {
            var latest = repository.findLatestDiagnosis(tenantId, incidentId);
            if (latest.isPresent() && isReusableLatest(incident, latest.get())) {
                return toResponse(latest.get());
            }
        }

        List<AiAlertRecord> alerts = repository.listIncidentAlerts(tenantId, incidentId);
        AiRcaRecord rca = repository.findLatestRca(tenantId, incidentId).orElse(null);

        AgentDiagnosisRequest agentRequest = new AgentDiagnosisRequest(
                AgentContract.DIAGNOSIS_CONTRACT_VERSION,
                tenantId,
                incidentId,
                toAgentIncident(incident),
                alerts.stream().map(this::toAgentAlert).toList(),
                rca == null ? null : toAgentRca(rca),
                normalized.normalizedLocale(),
                UUID.randomUUID().toString()
        );

        contractValidator.validateRequest(agentRequest);

        AgentDiagnosisResponse agentResponse = sanitizeAgentResponse(agentClient.diagnose(agentRequest));
        contractValidator.validateResponse(agentResponse);

        String diagnosisId = newId("diag");
        String requestJson = writeJson(agentRequest);
        String rawJson = writeJson(agentResponse.raw() == null ? Map.of() : agentResponse.raw());
        String nextStepsJson = writeJson(agentResponse.nextSteps());
        String runbookSuggestionsJson = writeJson(agentResponse.runbookSuggestions());
        String risksJson = writeJson(agentResponse.risks());

        repository.saveDiagnosis(
                diagnosisId,
                tenantId,
                incidentId,
                agentResponse,
                requestJson,
                rawJson,
                nextStepsJson,
                runbookSuggestionsJson,
                risksJson
        );

        repository.addIncidentTimeline(
                newId("tl"),
                incidentId,
                OffsetDateTime.now(),
                "AI diagnosis completed",
                agentResponse.summary(),
                writeJson(Map.of(
                        "contractVersion", agentResponse.contractVersion(),
                        "traceId", agentRequest.traceId(),
                        "aiDiagnosisId", diagnosisId,
                        "provider", agentResponse.provider(),
                        "model", agentResponse.model(),
                        "agentName", agentResponse.agentName(),
                        "rootCause", agentResponse.rootCause()
                ))
        );

        return repository.findDiagnosis(tenantId, diagnosisId)
                .map(this::toResponse)
                .orElseThrow(() -> new AppException("AI_DIAGNOSIS_NOT_FOUND", "AI diagnosis not found after save"));
    }

    private AgentDiagnosisResponse sanitizeAgentResponse(AgentDiagnosisResponse response) {
        if (response == null) {
            throw new AppException("AI_AGENT_EMPTY_RESPONSE", "AI agent returned empty response");
        }

        return new AgentDiagnosisResponse(
                blankToDefault(response.contractVersion(), AgentContract.DIAGNOSIS_CONTRACT_VERSION),
                blankToDefault(response.provider(), "aiops-agent"),
                blankToDefault(response.model(), "langgraph-deterministic"),
                blankToDefault(response.agentName(), "aegisops_diagnosis_graph"),
                blankToDefault(response.summary(), "No summary generated."),
                blankToDefault(response.rootCause(), "No root cause generated."),
                blankToDefault(response.impact(), "Impact is unknown."),
                response.nextSteps() == null ? List.of() : response.nextSteps(),
                response.runbookSuggestions() == null ? List.of() : response.runbookSuggestions(),
                response.risks() == null ? List.of() : response.risks(),
                response.raw() == null ? Map.of() : response.raw()
        );
    }

    private boolean isReusableLatest(AiIncidentRecord incident, AiDiagnosisRecord latest) {
        if (latest.createdAt() == null) {
            return false;
        }

        OffsetDateTime baseline = incident.lastSeenAt();
        if (baseline == null) {
            baseline = incident.updatedAt();
        }
        if (baseline == null) {
            baseline = incident.createdAt();
        }

        return baseline != null && !latest.createdAt().isBefore(baseline);
    }

    private AgentIncidentContext toAgentIncident(AiIncidentRecord incident) {
        return new AgentIncidentContext(
                incident.id(),
                incident.title(),
                incident.summary(),
                incident.severity(),
                incident.status(),
                incident.source(),
                incident.primaryAssetId(),
                incident.aggregationKey(),
                incident.alertCount(),
                incident.suspectedRootCause(),
                incident.confidence(),
                incident.startedAt(),
                incident.detectedAt(),
                incident.lastSeenAt()
        );
    }

    private AgentAlertContext toAgentAlert(AiAlertRecord alert) {
        return new AgentAlertContext(
                alert.id(),
                alert.source(),
                alert.sourceEventId(),
                alert.severity(),
                alert.title(),
                alert.description(),
                alert.assetId(),
                alert.entityType(),
                alert.entityName(),
                alert.fingerprint(),
                alert.labelsJson(),
                alert.startsAt()
        );
    }

    private AgentRcaContext toAgentRca(AiRcaRecord rca) {
        return new AgentRcaContext(
                rca.id(),
                rca.suspectedRootCause(),
                rca.confidence(),
                rca.summary(),
                rca.evidenceJson(),
                rca.modelVersion(),
                rca.createdAt()
        );
    }

    private AiDiagnosisResponse toResponse(AiDiagnosisRecord record) {
        return new AiDiagnosisResponse(
                record.id(),
                record.incidentId(),
                record.status(),
                record.provider(),
                record.model(),
                record.agentName(),
                record.summary(),
                record.rootCause(),
                record.impact(),
                readStringList(record.nextStepsJson()),
                readStringList(record.runbookSuggestionsJson()),
                readStringList(record.risksJson()),
                record.createdAt()
        );
    }

    private void ensureIncidentExists(String tenantId, String incidentId) {
        if (repository.findIncident(tenantId, incidentId).isEmpty()) {
            throw new AppException("INCIDENT_NOT_FOUND", "Incident not found");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new AppException("AI_DIAGNOSIS_INVALID", "Failed to serialize AI diagnosis payload");
        }
    }

    private List<String> readStringList(String json) {
        try {
            if (json == null || json.isBlank()) {
                return List.of();
            }
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception ex) {
            throw new AppException("AI_DIAGNOSIS_INVALID", "AI diagnosis JSON is invalid");
        }
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String newId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }
}
```

---

# 6. Java 单元测试

## 6.1 `AgentContractValidatorTest.java`

```java id="8x9o63"
package io.aegisops.ai.client;

import io.aegisops.ai.client.dto.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AgentContractValidatorTest {
    private final AgentContractValidator validator = new AgentContractValidator();

    @Test
    void validatesValidRequest() {
        assertDoesNotThrow(() -> validator.validateRequest(validRequest()));
    }

    @Test
    void rejectsInvalidContractVersionInRequest() {
        AgentDiagnosisRequest request = new AgentDiagnosisRequest(
                "bad",
                "tenant_1",
                "inc_1",
                new AgentIncidentContext("inc_1", null, null, null, null, null, null, null, 0, null, null, null, null, null),
                List.of(),
                null,
                "zh-CN",
                "trace_1"
        );

        AgentContractViolationException ex = assertThrows(
                AgentContractViolationException.class,
                () -> validator.validateRequest(request)
        );

        assertEquals("AI_AGENT_CONTRACT_VIOLATION", ex.errorCode());
    }

    @Test
    void rejectsMissingIncidentInRequest() {
        AgentDiagnosisRequest request = new AgentDiagnosisRequest(
                AgentContract.DIAGNOSIS_CONTRACT_VERSION,
                "tenant_1",
                "inc_1",
                null,
                List.of(),
                null,
                "zh-CN",
                "trace_1"
        );

        assertThrows(AgentContractViolationException.class, () -> validator.validateRequest(request));
    }

    @Test
    void validatesValidResponse() {
        assertDoesNotThrow(() -> validator.validateResponse(validResponse()));
    }

    @Test
    void rejectsUnsafeResponse() {
        AgentDiagnosisResponse response = new AgentDiagnosisResponse(
                AgentContract.DIAGNOSIS_CONTRACT_VERSION,
                "aiops-agent",
                "model",
                "agent",
                "summary",
                "root",
                "impact",
                List.of("Run rm -rf / automatically"),
                List.of(),
                List.of(),
                Map.of()
        );

        AgentContractViolationException ex = assertThrows(
                AgentContractViolationException.class,
                () -> validator.validateResponse(response)
        );

        assertTrue(ex.getMessage().contains("forbidden unsafe action keyword"));
    }

    private AgentDiagnosisRequest validRequest() {
        return new AgentDiagnosisRequest(
                AgentContract.DIAGNOSIS_CONTRACT_VERSION,
                "tenant_1",
                "inc_1",
                new AgentIncidentContext("inc_1", null, null, null, null, null, null, null, 0, null, null, null, null, null),
                List.of(new AgentAlertContext("alert_1", null, null, null, null, null, null, null, null, null, null, null)),
                null,
                "zh-CN",
                "trace_1"
        );
    }

    private AgentDiagnosisResponse validResponse() {
        return new AgentDiagnosisResponse(
                AgentContract.DIAGNOSIS_CONTRACT_VERSION,
                "aiops-agent",
                "langgraph-deterministic",
                "aegisops_diagnosis_graph",
                "summary",
                "root",
                "impact",
                List.of("step"),
                List.of("runbook"),
                List.of("risk"),
                Map.of("ok", true)
        );
    }
}
```

---

## 6.2 `HttpAiAgentClientTest.java`

```java id="ggh854"
package io.aegisops.ai.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.ai.client.dto.AgentDiagnosisRequest;
import io.aegisops.ai.client.dto.AgentDiagnosisResponse;
import io.aegisops.ai.client.dto.AgentIncidentContext;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpAiAgentClientTest {
    @Test
    void postsDiagnosisRequestWithContractHeaders() {
        ObjectMapper objectMapper = new ObjectMapper();
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

        AgentClientProperties properties = new AgentClientProperties(
                "http://agent:9008",
                "test-token",
                1000,
                1000
        );

        HttpAiAgentClient client = new HttpAiAgentClient(
                properties,
                objectMapper,
                restTemplate,
                new AgentContractValidator()
        );

        server.expect(requestTo("http://agent:9008/v1/diagnose"))
                .andExpect(header(AgentContract.INTERNAL_TOKEN_HEADER, "test-token"))
                .andExpect(header(AgentContract.TRACE_ID_HEADER, "trace_1"))
                .andExpect(header(AgentContract.CONTRACT_VERSION_HEADER, AgentContract.DIAGNOSIS_CONTRACT_VERSION))
                .andRespond(withSuccess("""
                        {
                          "contractVersion": "agent-diagnosis.v1",
                          "provider": "aiops-agent",
                          "model": "langgraph-deterministic",
                          "agentName": "aegisops_diagnosis_graph",
                          "summary": "summary",
                          "rootCause": "root",
                          "impact": "impact",
                          "nextSteps": ["step"],
                          "runbookSuggestions": ["runbook"],
                          "risks": ["risk"],
                          "raw": {"ok": true}
                        }
                        """, MediaType.APPLICATION_JSON));

        AgentDiagnosisResponse response = client.diagnose(new AgentDiagnosisRequest(
                AgentContract.DIAGNOSIS_CONTRACT_VERSION,
                "tenant_1",
                "inc_1",
                new AgentIncidentContext("inc_1", null, null, null, null, null, null, null, 0, null, null, null, null, null),
                List.of(),
                null,
                "zh-CN",
                "trace_1"
        ));

        assertEquals("aiops-agent", response.provider());
        assertEquals("aegisops_diagnosis_graph", response.agentName());
        assertEquals("root", response.rootCause());

        server.verify();
    }
}
```

---

## 6.3 `AiDiagnosisServiceTest.java`

```java id="4i0iby"
package io.aegisops.ai.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.aegisops.ai.client.dto.*;
import io.aegisops.common.exception.AppException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class AiDiagnosisServiceTest {
    @Test
    void recomputesAndSavesContractedDiagnosis() {
        FakeAiRepository repository = new FakeAiRepository();
        repository.incident = incident();
        repository.alerts = List.of(alert());
        repository.rca = rca();

        AiDiagnosisService service = new AiDiagnosisService(repository, new FakeAgentClient(), objectMapper());

        AiDiagnosisResponse response = service.diagnose("tenant_1", "inc_1", new AiDiagnoseRequest(true, "zh-CN"));

        assertEquals(1, repository.savedCount);
        assertEquals(1, repository.timelineCount);
        assertEquals("aegisops_diagnosis_graph", response.agentName());
        assertTrue(repository.lastRequestJson.contains("agent-diagnosis.v1"));
    }

    @Test
    void returnsFreshLatestWhenForceDisabled() {
        FakeAiRepository repository = new FakeAiRepository();
        repository.incident = incident();
        repository.latest = diagnosis(repository.incident.lastSeenAt().plusSeconds(1));

        AiDiagnosisService service = new AiDiagnosisService(repository, new FakeAgentClient(), objectMapper());

        AiDiagnosisResponse response = service.diagnose("tenant_1", "inc_1", new AiDiagnoseRequest(false, "zh-CN"));

        assertEquals("diag_cached", response.id());
        assertEquals(0, repository.savedCount);
        assertEquals(0, repository.timelineCount);
    }

    @Test
    void rejectsUnsafeAgentResponse() {
        FakeAiRepository repository = new FakeAiRepository();
        repository.incident = incident();

        AiDiagnosisService service = new AiDiagnosisService(repository, new UnsafeAgentClient(), objectMapper());

        AppException ex = assertThrows(AppException.class, () ->
                service.diagnose("tenant_1", "inc_1", new AiDiagnoseRequest(true, "zh-CN"))
        );

        assertEquals("AI_AGENT_CONTRACT_VIOLATION", ex.errorCode());
    }

    private ObjectMapper objectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    private AiIncidentRecord incident() {
        OffsetDateTime now = OffsetDateTime.parse("2026-06-14T10:00:00+09:00");
        return new AiIncidentRecord(
                "inc_1",
                "tenant_1",
                "CPU high",
                "summary",
                "critical",
                "open",
                "system",
                "asset_1",
                "zabbix:fp_cpu",
                2,
                "CPU saturation",
                new BigDecimal("0.8000"),
                now.minusMinutes(5),
                now,
                now,
                now,
                now
        );
    }

    private AiAlertRecord alert() {
        OffsetDateTime now = OffsetDateTime.parse("2026-06-14T10:00:00+09:00");
        return new AiAlertRecord(
                "alert_1",
                "zabbix",
                "event_1",
                "critical",
                "CPU high",
                "CPU is high",
                "asset_1",
                "host",
                "host-1",
                "fp_cpu",
                "{}",
                now
        );
    }

    private AiRcaRecord rca() {
        OffsetDateTime now = OffsetDateTime.parse("2026-06-14T10:00:00+09:00");
        return new AiRcaRecord(
                "rca_1",
                "CPU saturation",
                new BigDecimal("0.8000"),
                "RCA summary",
                "[]",
                "rules-v1",
                now
        );
    }

    private AiDiagnosisRecord diagnosis(OffsetDateTime createdAt) {
        return new AiDiagnosisRecord(
                "diag_cached",
                "tenant_1",
                "inc_1",
                "completed",
                "aiops-agent",
                "langgraph-deterministic",
                "aegisops_diagnosis_graph",
                "cached summary",
                "cached root",
                "cached impact",
                "[\"step\"]",
                "[]",
                "[]",
                createdAt
        );
    }

    private static final class FakeAgentClient implements AiAgentClient {
        @Override
        public AgentDiagnosisResponse diagnose(AgentDiagnosisRequest request) {
            assertEquals(AgentContract.DIAGNOSIS_CONTRACT_VERSION, request.contractVersion());
            assertNotNull(request.traceId());

            return new AgentDiagnosisResponse(
                    AgentContract.DIAGNOSIS_CONTRACT_VERSION,
                    "aiops-agent",
                    "langgraph-deterministic",
                    "aegisops_diagnosis_graph",
                    "AI summary",
                    "CPU saturation",
                    "Service latency may increase.",
                    List.of("Check CPU usage", "Check top process"),
                    List.of("Host resource saturation runbook"),
                    List.of("Do not restart blindly"),
                    Map.of("contractVersion", request.contractVersion())
            );
        }
    }

    private static final class UnsafeAgentClient implements AiAgentClient {
        @Override
        public AgentDiagnosisResponse diagnose(AgentDiagnosisRequest request) {
            return new AgentDiagnosisResponse(
                    AgentContract.DIAGNOSIS_CONTRACT_VERSION,
                    "aiops-agent",
                    "langgraph-deterministic",
                    "aegisops_diagnosis_graph",
                    "AI summary",
                    "bad root",
                    "bad impact",
                    List.of("自动执行删除 namespace"),
                    List.of(),
                    List.of(),
                    Map.of()
            );
        }
    }

    private static final class FakeAiRepository implements AiRepository {
        AiIncidentRecord incident;
        List<AiAlertRecord> alerts = List.of();
        AiRcaRecord rca;
        AiDiagnosisRecord latest;
        AiDiagnosisRecord saved;
        int savedCount;
        int timelineCount;
        String lastRequestJson;

        @Override
        public Optional<AiIncidentRecord> findIncident(String tenantId, String incidentId) {
            if (incident == null) {
                return Optional.empty();
            }
            if (!incident.tenantId().equals(tenantId) || !incident.id().equals(incidentId)) {
                return Optional.empty();
            }
            return Optional.of(incident);
        }

        @Override
        public List<AiAlertRecord> listIncidentAlerts(String tenantId, String incidentId) {
            return alerts;
        }

        @Override
        public Optional<AiRcaRecord> findLatestRca(String tenantId, String incidentId) {
            return Optional.ofNullable(rca);
        }

        @Override
        public Optional<AiDiagnosisRecord> findLatestDiagnosis(String tenantId, String incidentId) {
            return Optional.ofNullable(latest);
        }

        @Override
        public Optional<AiDiagnosisRecord> findDiagnosis(String tenantId, String diagnosisId) {
            if (saved == null || !saved.tenantId().equals(tenantId) || !saved.id().equals(diagnosisId)) {
                return Optional.empty();
            }
            return Optional.of(saved);
        }

        @Override
        public void saveDiagnosis(
                String id,
                String tenantId,
                String incidentId,
                AgentDiagnosisResponse response,
                String requestJson,
                String rawJson,
                String nextStepsJson,
                String runbookSuggestionsJson,
                String risksJson
        ) {
            savedCount++;
            lastRequestJson = requestJson;
            saved = new AiDiagnosisRecord(
                    id,
                    tenantId,
                    incidentId,
                    "completed",
                    response.provider(),
                    response.model(),
                    response.agentName(),
                    response.summary(),
                    response.rootCause(),
                    response.impact(),
                    nextStepsJson,
                    runbookSuggestionsJson,
                    risksJson,
                    OffsetDateTime.parse("2026-06-14T10:00:00+09:00")
            );
        }

        @Override
        public void addIncidentTimeline(String id, String incidentId, OffsetDateTime eventTime, String title, String description, String payloadJson) {
            timelineCount++;
        }
    }
}
```

---

# 7. Python 代码

## 7.1 `apps/aiops-agent/src/aiops_agent/settings.py`

```python id="bzskp9"
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="AIOPS_AGENT_", env_file=".env", extra="ignore")

    internal_token: str = "dev-internal-token"

    provider: str = "aiops-agent"
    model: str = "langgraph-deterministic"
    agent_name: str = "aegisops_diagnosis_graph"
    default_locale: str = "zh-CN"
    contract_version: str = "agent-diagnosis.v1"

    # deterministic | openai-compatible
    generation_mode: str = "deterministic"

    def normalized_generation_mode(self) -> str:
        value = (self.generation_mode or "deterministic").strip().lower()
        if value not in {"deterministic", "openai-compatible"}:
            return "deterministic"
        return value


settings = Settings()
```

---

## 7.2 `apps/aiops-agent/src/aiops_agent/schemas.py`

```python id="tocelh"
from __future__ import annotations

from datetime import datetime
from typing import Any

from pydantic import BaseModel, Field


class IncidentContext(BaseModel):
    id: str
    title: str | None = None
    summary: str | None = None
    severity: str | None = None
    status: str | None = None
    source: str | None = None
    primaryAssetId: str | None = None
    aggregationKey: str | None = None
    alertCount: int = 0
    suspectedRootCause: str | None = None
    confidence: float | None = None
    startedAt: datetime | None = None
    detectedAt: datetime | None = None
    lastSeenAt: datetime | None = None


class AlertContext(BaseModel):
    id: str
    source: str | None = None
    sourceEventId: str | None = None
    severity: str | None = None
    title: str | None = None
    description: str | None = None
    assetId: str | None = None
    entityType: str | None = None
    entityName: str | None = None
    fingerprint: str | None = None
    labelsJson: str | None = None
    startsAt: datetime | None = None


class RcaContext(BaseModel):
    id: str
    suspectedRootCause: str | None = None
    confidence: float | None = None
    summary: str | None = None
    evidenceJson: str | None = None
    modelVersion: str | None = None
    createdAt: datetime | None = None


class DiagnoseRequest(BaseModel):
    contractVersion: str = "agent-diagnosis.v1"
    tenantId: str
    incidentId: str
    incident: IncidentContext
    alerts: list[AlertContext] = Field(default_factory=list)
    rca: RcaContext | None = None
    locale: str = "zh-CN"
    traceId: str


class DiagnoseResponse(BaseModel):
    contractVersion: str = "agent-diagnosis.v1"
    provider: str = "aiops-agent"
    model: str = "langgraph-deterministic"
    agentName: str = "aegisops_diagnosis_graph"
    summary: str
    rootCause: str
    impact: str
    nextSteps: list[str] = Field(default_factory=list)
    runbookSuggestions: list[str] = Field(default_factory=list)
    risks: list[str] = Field(default_factory=list)
    raw: dict[str, Any] = Field(default_factory=dict)


class HealthResponse(BaseModel):
    ok: bool
    provider: str
    model: str
    agentName: str
    contractVersion: str
    generationMode: str


class ContractResponse(BaseModel):
    contractVersion: str
    requestSchema: dict[str, Any]
    responseSchema: dict[str, Any]
```

---

## 7.3 `apps/aiops-agent/src/aiops_agent/contract.py`

```python id="pao635"
from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from fastapi import HTTPException, status

from aiops_agent.schemas import DiagnoseRequest, DiagnoseResponse
from aiops_agent.settings import Settings


def contract_root() -> Path:
    current = Path(__file__).resolve()

    for parent in current.parents:
        candidate = parent / "contracts" / "agent"
        if candidate.exists():
            return candidate

        candidate = parent.parent / "contracts" / "agent"
        if candidate.exists():
            return candidate

    fallback = current.parents[4] / "contracts" / "agent"
    return fallback


def load_contract_schema(name: str) -> dict[str, Any]:
    path = contract_root() / name
    return json.loads(path.read_text(encoding="utf-8"))


def validate_request_contract(request: DiagnoseRequest, settings: Settings) -> None:
    if request.contractVersion != settings.contract_version:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"unsupported contractVersion: {request.contractVersion}",
        )

    if not request.traceId.strip():
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="traceId is required",
        )

    if request.incident.id != request.incidentId:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="incident.id must equal incidentId",
        )


def validate_response_contract(response: DiagnoseResponse, settings: Settings) -> None:
    if response.contractVersion != settings.contract_version:
        raise RuntimeError(f"invalid response contractVersion: {response.contractVersion}")

    required_text = {
        "provider": response.provider,
        "model": response.model,
        "agentName": response.agentName,
        "summary": response.summary,
        "rootCause": response.rootCause,
        "impact": response.impact,
    }

    for name, value in required_text.items():
        if value is None or not str(value).strip():
            raise RuntimeError(f"response.{name} is required")

    if response.nextSteps is None:
        raise RuntimeError("response.nextSteps is required")
    if response.runbookSuggestions is None:
        raise RuntimeError("response.runbookSuggestions is required")
    if response.risks is None:
        raise RuntimeError("response.risks is required")
    if response.raw is None:
        raise RuntimeError("response.raw is required")
```

---

## 7.4 `apps/aiops-agent/src/aiops_agent/safety.py`

```python id="p5364b"
from __future__ import annotations

from aiops_agent.schemas import DiagnoseResponse


FORBIDDEN_AUTO_EXECUTION_KEYWORDS = [
    "rm -rf",
    "drop database",
    "truncate table",
    "delete namespace",
    "kubectl delete",
    "format disk",
    "shutdown -h",
    "reboot now",
    "无需审批自动",
    "自动执行删除",
    "自动清空",
]

MANDATORY_RISKS = [
    "Do not execute remediation automatically in Phase4.1.",
    "All remediation actions require human confirmation in later phases.",
]


def find_forbidden_keywords(response: DiagnoseResponse) -> list[str]:
    text = "\n".join([
        response.summary,
        response.rootCause,
        response.impact,
        *response.nextSteps,
        *response.runbookSuggestions,
        *response.risks,
    ]).lower()

    return [keyword for keyword in FORBIDDEN_AUTO_EXECUTION_KEYWORDS if keyword in text]


def apply_safety_boundary(response: DiagnoseResponse) -> DiagnoseResponse:
    blocked = find_forbidden_keywords(response)

    risks = list(response.risks)
    for risk in MANDATORY_RISKS:
        if risk not in risks:
            risks.append(risk)

    raw = dict(response.raw)
    raw["safety"] = {
        "blockedKeywords": blocked,
        "autoExecutionAllowed": False,
    }

    if blocked:
        risks.append(
            "Potentially unsafe remediation wording was detected and must be reviewed manually."
        )

    return response.model_copy(update={
        "risks": risks,
        "raw": raw,
    })


def assert_safe_response(response: DiagnoseResponse) -> None:
    blocked = find_forbidden_keywords(response)
    if blocked:
        raise RuntimeError(f"unsafe auto-execution keyword detected: {', '.join(blocked)}")
```

---

## 7.5 `apps/aiops-agent/src/aiops_agent/graph.py`

```python id="0pwxik"
from __future__ import annotations

from typing import Any, TypedDict

from langgraph.graph import END, START, StateGraph

from aiops_agent.safety import apply_safety_boundary
from aiops_agent.schemas import DiagnoseRequest, DiagnoseResponse
from aiops_agent.settings import Settings
from aiops_agent.tools import (
    inspect_alerts,
    inspect_rca_evidence,
    query_logs_stub,
    query_metrics_stub,
    safety_guard,
    search_runbooks_stub,
    summarize_incident_context,
)


class DiagnosisState(TypedDict, total=False):
    request: DiagnoseRequest
    incident_summary: dict[str, Any]
    alert_analysis: dict[str, Any]
    rca_analysis: dict[str, Any]
    metrics: dict[str, Any]
    logs: dict[str, Any]
    runbook_suggestions: list[str]
    risks: list[str]
    diagnosis: DiagnoseResponse


def load_context(state: DiagnosisState) -> DiagnosisState:
    request = state["request"]
    return {
        "incident_summary": summarize_incident_context(request),
    }


def analyze_alerts(state: DiagnosisState) -> DiagnosisState:
    request = state["request"]
    return {
        "alert_analysis": inspect_alerts(request.alerts),
    }


def analyze_rca(state: DiagnosisState) -> DiagnosisState:
    request = state["request"]
    return {
        "rca_analysis": inspect_rca_evidence(request),
    }


def query_metrics(state: DiagnosisState) -> DiagnosisState:
    return {
        "metrics": query_metrics_stub(state["request"]),
    }


def query_logs(state: DiagnosisState) -> DiagnosisState:
    return {
        "logs": query_logs_stub(state["request"]),
    }


def search_runbooks(state: DiagnosisState) -> DiagnosisState:
    return {
        "runbook_suggestions": search_runbooks_stub(
            state["request"],
            state.get("alert_analysis", {}),
            state.get("rca_analysis", {}),
        )
    }


def safety_check(state: DiagnosisState) -> DiagnosisState:
    return {
        "risks": safety_guard(),
    }


def generate_diagnosis(settings: Settings):
    def _node(state: DiagnosisState) -> DiagnosisState:
        request = state["request"]
        incident = state.get("incident_summary", {})
        alerts = state.get("alert_analysis", {})
        rca = state.get("rca_analysis", {})
        runbooks = state.get("runbook_suggestions", [])
        risks = state.get("risks", [])

        root_cause = (
            rca.get("rootCause")
            or incident.get("suspectedRootCause")
            or "No strong root cause has been confirmed. Start from the dominant alert and primary asset."
        )

        dominant_asset = (
            alerts.get("dominantAssetId")
            or incident.get("primaryAssetId")
            or "unknown asset"
        )

        dominant_fingerprint = (
            alerts.get("dominantFingerprint")
            or incident.get("aggregationKey")
            or "unknown fingerprint"
        )

        summary = (
            f"Incident {request.incidentId} is {incident.get('status', 'unknown')} "
            f"with severity {incident.get('severity', alerts.get('topSeverity', 'info'))}. "
            f"{alerts.get('count', 0)} linked alert(s) were analyzed."
        )

        impact = (
            f"The primary impact may be concentrated on {dominant_asset}. "
            "Downstream services may be affected if this asset is part of a dependency path."
        )

        next_steps = [
            f"Confirm whether the dominant fingerprint `{dominant_fingerprint}` is still firing.",
            f"Check the primary asset `{dominant_asset}` around the incident start time.",
            "Compare metrics before and after incident detection.",
            "Review recent deployments, restarts, configuration changes, and dependency health.",
            "Validate RCA evidence before taking remediation action.",
        ]

        raw = {
            "graph": "aegisops_diagnosis_graph",
            "contractVersion": settings.contract_version,
            "traceId": request.traceId,
            "generationMode": settings.normalized_generation_mode(),
            "incident": incident,
            "alerts": alerts,
            "rca": rca,
            "metrics": state.get("metrics", {}),
            "logs": state.get("logs", {}),
        }

        response = DiagnoseResponse(
            contractVersion=settings.contract_version,
            provider=settings.provider,
            model=settings.model,
            agentName=settings.agent_name,
            summary=summary,
            rootCause=str(root_cause),
            impact=impact,
            nextSteps=next_steps,
            runbookSuggestions=runbooks,
            risks=risks,
            raw=raw,
        )

        return {
            "diagnosis": apply_safety_boundary(response),
        }

    return _node


def build_diagnosis_graph(settings: Settings):
    graph = StateGraph(DiagnosisState)

    graph.add_node("load_context", load_context)
    graph.add_node("analyze_alerts", analyze_alerts)
    graph.add_node("analyze_rca", analyze_rca)
    graph.add_node("query_metrics", query_metrics)
    graph.add_node("query_logs", query_logs)
    graph.add_node("search_runbooks", search_runbooks)
    graph.add_node("safety_check", safety_check)
    graph.add_node("generate_diagnosis", generate_diagnosis(settings))

    graph.add_edge(START, "load_context")
    graph.add_edge("load_context", "analyze_alerts")
    graph.add_edge("analyze_alerts", "analyze_rca")
    graph.add_edge("analyze_rca", "query_metrics")
    graph.add_edge("query_metrics", "query_logs")
    graph.add_edge("query_logs", "search_runbooks")
    graph.add_edge("search_runbooks", "safety_check")
    graph.add_edge("safety_check", "generate_diagnosis")
    graph.add_edge("generate_diagnosis", END)

    return graph.compile()


def run_diagnosis_graph(request: DiagnoseRequest, settings: Settings) -> DiagnoseResponse:
    compiled = build_diagnosis_graph(settings)
    result = compiled.invoke({"request": request})
    diagnosis = result.get("diagnosis")
    if not isinstance(diagnosis, DiagnoseResponse):
        raise RuntimeError("diagnosis graph did not return DiagnoseResponse")
    return diagnosis
```

---

## 7.6 `apps/aiops-agent/src/aiops_agent/main.py`

```python id="lozkw8"
from __future__ import annotations

from fastapi import Depends, FastAPI, Header, HTTPException, status

from aiops_agent.contract import (
    load_contract_schema,
    validate_request_contract,
    validate_response_contract,
)
from aiops_agent.schemas import ContractResponse, DiagnoseRequest, DiagnoseResponse, HealthResponse
from aiops_agent.service import DiagnosisService
from aiops_agent.settings import settings

app = FastAPI(title="AegisOps LangGraph Agent Runtime", version="0.1.0")


def verify_internal_token(x_aegisops_internal_token: str | None = Header(default=None)) -> None:
    if x_aegisops_internal_token != settings.internal_token:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="invalid internal token",
        )


def verify_contract_version(x_aegisops_contract_version: str | None = Header(default=None)) -> None:
    if x_aegisops_contract_version is None:
        return

    if x_aegisops_contract_version != settings.contract_version:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"unsupported contract version: {x_aegisops_contract_version}",
        )


def diagnosis_service() -> DiagnosisService:
    return DiagnosisService(settings)


@app.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    return HealthResponse(
        ok=True,
        provider=settings.provider,
        model=settings.model,
        agentName=settings.agent_name,
        contractVersion=settings.contract_version,
        generationMode=settings.normalized_generation_mode(),
    )


@app.get("/v1/contracts/diagnosis", response_model=ContractResponse)
def diagnosis_contract() -> ContractResponse:
    return ContractResponse(
        contractVersion=settings.contract_version,
        requestSchema=load_contract_schema("diagnosis-request.schema.json"),
        responseSchema=load_contract_schema("diagnosis-response.schema.json"),
    )


@app.post(
    "/v1/diagnose",
    response_model=DiagnoseResponse,
    dependencies=[Depends(verify_internal_token), Depends(verify_contract_version)],
)
def diagnose(
    request: DiagnoseRequest,
    service: DiagnosisService = Depends(diagnosis_service),
) -> DiagnoseResponse:
    validate_request_contract(request, settings)
    response = service.diagnose(request)
    validate_response_contract(response, settings)
    return response
```

---

# 8. Python 单元测试

## 8.1 `tests/test_contract.py`

```python id="pq5qma"
from fastapi import HTTPException
import pytest

from aiops_agent.contract import (
    load_contract_schema,
    validate_request_contract,
    validate_response_contract,
)
from aiops_agent.schemas import DiagnoseRequest, DiagnoseResponse, IncidentContext
from aiops_agent.settings import Settings


def test_load_contract_schema():
    schema = load_contract_schema("diagnosis-request.schema.json")

    assert schema["title"] == "AegisOps Agent Diagnosis Request"
    assert "contractVersion" in schema["required"]


def test_validate_request_contract_accepts_valid_request():
    settings = Settings(contract_version="agent-diagnosis.v1")

    request = DiagnoseRequest(
        contractVersion="agent-diagnosis.v1",
        tenantId="tenant_1",
        incidentId="inc_1",
        incident=IncidentContext(id="inc_1"),
        traceId="trace_1",
    )

    validate_request_contract(request, settings)


def test_validate_request_contract_rejects_wrong_version():
    settings = Settings(contract_version="agent-diagnosis.v1")

    request = DiagnoseRequest(
        contractVersion="bad",
        tenantId="tenant_1",
        incidentId="inc_1",
        incident=IncidentContext(id="inc_1"),
        traceId="trace_1",
    )

    with pytest.raises(HTTPException):
        validate_request_contract(request, settings)


def test_validate_response_contract_accepts_valid_response():
    settings = Settings(contract_version="agent-diagnosis.v1")

    response = DiagnoseResponse(
        contractVersion="agent-diagnosis.v1",
        provider="aiops-agent",
        model="langgraph-deterministic",
        agentName="aegisops_diagnosis_graph",
        summary="summary",
        rootCause="root",
        impact="impact",
        nextSteps=[],
        runbookSuggestions=[],
        risks=[],
        raw={},
    )

    validate_response_contract(response, settings)
```

---

## 8.2 `tests/test_safety.py`

```python id="hhmch8"
import pytest

from aiops_agent.safety import apply_safety_boundary, assert_safe_response, find_forbidden_keywords
from aiops_agent.schemas import DiagnoseResponse


def response_with_step(step: str) -> DiagnoseResponse:
    return DiagnoseResponse(
        contractVersion="agent-diagnosis.v1",
        provider="aiops-agent",
        model="langgraph-deterministic",
        agentName="aegisops_diagnosis_graph",
        summary="summary",
        rootCause="root",
        impact="impact",
        nextSteps=[step],
        runbookSuggestions=[],
        risks=[],
        raw={},
    )


def test_find_forbidden_keywords_detects_rm_rf():
    response = response_with_step("run rm -rf / automatically")

    result = find_forbidden_keywords(response)

    assert "rm -rf" in result


def test_apply_safety_boundary_adds_mandatory_risks():
    response = response_with_step("check cpu usage")

    safe = apply_safety_boundary(response)

    assert safe.raw["safety"]["autoExecutionAllowed"] is False
    assert any("Do not execute remediation automatically" in risk for risk in safe.risks)


def test_assert_safe_response_raises_for_unsafe_keyword():
    response = response_with_step("kubectl delete namespace prod")

    with pytest.raises(RuntimeError):
        assert_safe_response(response)
```

---

## 8.3 `tests/test_graph.py`

```python id="rz9o82"
from aiops_agent.graph import run_diagnosis_graph
from aiops_agent.schemas import AlertContext, DiagnoseRequest, IncidentContext, RcaContext
from aiops_agent.settings import Settings


def test_diagnosis_graph_returns_contract_version_and_safety_raw():
    settings = Settings(
        contract_version="agent-diagnosis.v1",
        provider="aiops-agent",
        model="langgraph-deterministic",
        agent_name="aegisops_diagnosis_graph",
    )

    request = DiagnoseRequest(
        contractVersion="agent-diagnosis.v1",
        tenantId="tenant_1",
        incidentId="inc_1",
        incident=IncidentContext(
            id="inc_1",
            title="CPU high",
            severity="critical",
            status="open",
            primaryAssetId="asset_1",
            alertCount=2,
        ),
        alerts=[
            AlertContext(id="a1", title="CPU high", severity="critical", assetId="asset_1", fingerprint="fp_cpu"),
            AlertContext(id="a2", title="CPU high", severity="warning", assetId="asset_1", fingerprint="fp_cpu"),
        ],
        rca=RcaContext(
            id="rca_1",
            suspectedRootCause="CPU saturation",
            confidence=0.8,
            summary="RCA summary",
        ),
        traceId="trace_1",
    )

    response = run_diagnosis_graph(request, settings)

    assert response.contractVersion == "agent-diagnosis.v1"
    assert response.provider == "aiops-agent"
    assert response.agentName == "aegisops_diagnosis_graph"
    assert response.raw["traceId"] == "trace_1"
    assert response.raw["safety"]["autoExecutionAllowed"] is False
    assert response.nextSteps
    assert response.risks
```

---

## 8.4 `tests/test_api.py`

```python id="96ro1e"
from fastapi.testclient import TestClient

from aiops_agent.main import app
from aiops_agent.settings import settings


client = TestClient(app)


def test_health_includes_contract_version():
    response = client.get("/health")

    assert response.status_code == 200
    body = response.json()
    assert body["ok"] is True
    assert body["contractVersion"] == settings.contract_version


def test_contract_endpoint_returns_schemas():
    response = client.get("/v1/contracts/diagnosis")

    assert response.status_code == 200
    body = response.json()
    assert body["contractVersion"] == settings.contract_version
    assert body["requestSchema"]["title"] == "AegisOps Agent Diagnosis Request"
    assert body["responseSchema"]["title"] == "AegisOps Agent Diagnosis Response"


def test_diagnose_rejects_missing_token():
    response = client.post(
        "/v1/diagnose",
        json={
            "contractVersion": "agent-diagnosis.v1",
            "tenantId": "tenant_1",
            "incidentId": "inc_1",
            "incident": {"id": "inc_1"},
            "alerts": [],
            "locale": "zh-CN",
            "traceId": "trace_1"
        },
    )

    assert response.status_code == 401


def test_diagnose_rejects_wrong_contract_header():
    response = client.post(
        "/v1/diagnose",
        headers={
            "X-AegisOps-Internal-Token": settings.internal_token,
            "X-AegisOps-Contract-Version": "bad"
        },
        json={
            "contractVersion": "agent-diagnosis.v1",
            "tenantId": "tenant_1",
            "incidentId": "inc_1",
            "incident": {"id": "inc_1"},
            "alerts": [],
            "locale": "zh-CN",
            "traceId": "trace_1"
        },
    )

    assert response.status_code == 400


def test_diagnose_returns_contract_response():
    response = client.post(
        "/v1/diagnose",
        headers={
            "X-AegisOps-Internal-Token": settings.internal_token,
            "X-AegisOps-Contract-Version": settings.contract_version
        },
        json={
            "contractVersion": "agent-diagnosis.v1",
            "tenantId": "tenant_1",
            "incidentId": "inc_1",
            "incident": {
                "id": "inc_1",
                "title": "CPU high",
                "severity": "critical",
                "status": "open",
                "primaryAssetId": "asset_1",
                "alertCount": 1
            },
            "alerts": [
                {
                    "id": "a1",
                    "title": "CPU high",
                    "severity": "critical",
                    "assetId": "asset_1",
                    "fingerprint": "fp_cpu"
                }
            ],
            "rca": {
                "id": "rca_1",
                "suspectedRootCause": "CPU saturation",
                "confidence": 0.8,
                "summary": "RCA summary"
            },
            "locale": "zh-CN",
            "traceId": "trace_1"
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["contractVersion"] == "agent-diagnosis.v1"
    assert body["agentName"] == "aegisops_diagnosis_graph"
    assert body["raw"]["traceId"] == "trace_1"
    assert body["raw"]["safety"]["autoExecutionAllowed"] is False
```

---

# 9. 配置补充

## 9.1 Python 环境变量

```yaml id="gp3mtt"
AIOPS_AGENT_INTERNAL_TOKEN: dev-internal-token
AIOPS_AGENT_PROVIDER: aiops-agent
AIOPS_AGENT_MODEL: langgraph-deterministic
AIOPS_AGENT_NAME: aegisops_diagnosis_graph
AIOPS_AGENT_CONTRACT_VERSION: agent-diagnosis.v1
AIOPS_AGENT_GENERATION_MODE: deterministic
```

## 9.2 Java 配置不变

```properties id="qv548l"
aiops.agent.base-url=http://localhost:9008
aiops.agent.internal-token=dev-internal-token
aiops.agent.connect-timeout-millis=3000
aiops.agent.read-timeout-millis=30000
```

---

# 10. 验证命令

## 10.1 Python

```powershell id="f6bx9w"
cd apps/aiops-agent
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -e ".[test]"
pytest
```

## 10.2 Java

```powershell id="fjdtld"
mvn -pl modules/aiops-ai-client -am test
mvn -pl apps/aiops-server -am test
```

## 10.3 手动验证 Contract Endpoint

```powershell id="a57wu8"
curl http://localhost:9008/health

curl http://localhost:9008/v1/contracts/diagnosis
```

## 10.4 手动验证 Diagnose

```powershell id="75d6nh"
curl -X POST http://localhost:9008/v1/diagnose `
  -H "Content-Type: application/json" `
  -H "X-AegisOps-Internal-Token: dev-internal-token" `
  -H "X-AegisOps-Contract-Version: agent-diagnosis.v1" `
  -d @contracts/agent/examples/diagnosis-request.example.json
```

---

# 11. Phase4.1 验收标准

```txt id="s5bnke"
1. /health 返回 contractVersion
2. /v1/contracts/diagnosis 返回 request/response schema
3. /v1/diagnose 缺 internal token 返回 401
4. /v1/diagnose contract header 错误返回 400
5. /v1/diagnose body contractVersion 错误返回 400
6. Python response 必带 contractVersion
7. Python response raw 必带 traceId
8. Python response raw.safety.autoExecutionAllowed=false
9. Java 请求 Python 前校验 request contract
10. Java 收到 Python 后校验 response contract
11. unsafe response 被 Java 拒绝
12. unsafe response 被 Python safety 检测
13. ai_diagnosis 落库流程不变
14. incident_timeline 写入 ai_diagnosed 流程不变
```

---

# 12. Phase4.1 完成后的路线状态

```txt id="itqogl"
Phase4.0：Python LangGraph OSS Diagnosis Agent
Phase4.1：Agent Context Contract & Safety Boundary
Phase4.2：OpenAI-Compatible LLM Provider + JSON Parser + Fallback
Phase4.3：Metrics / Logs / Changes Tool Integration
Phase4.4：Agent Run Tracing & Eval
Phase4.5：jOOQ Persistence Refactor
Phase5.0：Runbook Recommendation
Phase5.1：Approval Workflow
Phase5.2：aiops-runner Execution
```
