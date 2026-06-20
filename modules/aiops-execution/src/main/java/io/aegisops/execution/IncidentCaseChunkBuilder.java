package io.aegisops.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.execution.dto.IncidentCaseResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class IncidentCaseChunkBuilder {
  private final KnowledgeBaseJson json;

  public IncidentCaseChunkBuilder(ObjectMapper objectMapper) {
    this.json = new KnowledgeBaseJson(objectMapper);
  }

  public List<KnowledgeBaseChunkDraft> build(IncidentCaseResponse incidentCase) {
    List<KnowledgeBaseChunkDraft> chunks = new ArrayList<>();
    int order = 1;

    chunks.add(
        new KnowledgeBaseChunkDraft(
            order++,
            incidentCase.title() + " - summary",
            """
            Title: %s
            Severity: %s
            Summary: %s
            Root Cause: %s
            Resolution: %s
            Prevention: %s
            Tags: %s
            """
                .formatted(
                    value(incidentCase.title()),
                    value(incidentCase.severity()),
                    value(incidentCase.summary()),
                    value(incidentCase.rootCause()),
                    value(incidentCase.resolution()),
                    value(incidentCase.prevention()),
                    String.join(", ", incidentCase.tags())),
            json.write(Map.of("section", "summary"))));

    for (var symptom : incidentCase.symptoms()) {
      chunks.add(
          new KnowledgeBaseChunkDraft(
              order++,
              incidentCase.title() + " - symptom - " + symptom.name(),
              """
              Symptom Type: %s
              Symptom Name: %s
              Description: %s
              """
                  .formatted(
                      value(symptom.symptomType()),
                      value(symptom.name()),
                      value(symptom.description())),
              json.write(Map.of("section", "symptom", "symptomId", symptom.id()))));
    }

    for (var step : incidentCase.resolutionSteps()) {
      chunks.add(
          new KnowledgeBaseChunkDraft(
              order++,
              incidentCase.title() + " - resolution step " + step.stepOrder(),
              """
              Resolution Step: %s
              Action Type: %s
              Description: %s
              """
                  .formatted(
                      value(step.title()), value(step.actionType()), value(step.description())),
              json.write(Map.of("section", "resolution_step", "stepId", step.id()))));
    }

    return chunks;
  }

  private String value(String value) {
    return value == null ? "" : value;
  }
}
