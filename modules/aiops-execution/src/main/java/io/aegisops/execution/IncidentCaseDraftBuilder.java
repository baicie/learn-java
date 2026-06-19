package io.aegisops.execution;

import io.aegisops.execution.dto.PostmortemActionItemRecord;
import io.aegisops.execution.dto.PostmortemReportRecord;
import io.aegisops.execution.dto.PostmortemSectionRecord;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class IncidentCaseDraftBuilder {
  public IncidentCaseDraft build(
      PostmortemReportRecord report,
      List<PostmortemSectionRecord> sections,
      List<PostmortemActionItemRecord> actionItems,
      List<String> requestTags) {
    List<IncidentCaseDraft.Symptom> symptoms = new ArrayList<>();
    List<IncidentCaseDraft.ResolutionStep> resolutionSteps = new ArrayList<>();
    LinkedHashSet<String> tags = new LinkedHashSet<>();

    addTag(tags, report.severity());
    addTag(tags, normalizeTag(report.rootCause()));

    for (String tag : requestTags == null ? List.<String>of() : requestTags) {
      addTag(tags, tag);
    }

    for (PostmortemSectionRecord section : sections) {
      switch (section.sectionType()) {
        case "impact", "detection" ->
            symptoms.add(
                new IncidentCaseDraft.Symptom(
                    section.sectionType(), section.title(), section.content()));
        case "resolution" ->
            resolutionSteps.add(
                new IncidentCaseDraft.ResolutionStep(
                    section.title(), section.content(), "manual", section.id()));
        default -> {
          // ignore non-case sections
        }
      }
    }

    int index = 1;
    for (PostmortemActionItemRecord actionItem : actionItems) {
      resolutionSteps.add(
          new IncidentCaseDraft.ResolutionStep(
              "Follow-up " + index++ + ": " + actionItem.title(),
              actionItem.description(),
              "follow_up",
              actionItem.id()));
    }

    if (symptoms.isEmpty()) {
      symptoms.add(new IncidentCaseDraft.Symptom("summary", "Incident summary", report.summary()));
    }

    if (resolutionSteps.isEmpty()) {
      resolutionSteps.add(
          new IncidentCaseDraft.ResolutionStep(
              "Review resolution",
              blankToFallback(report.resolution(), "Resolution requires manual review."),
              "manual",
              report.id()));
    }

    return new IncidentCaseDraft(
        report.title(),
        report.summary(),
        report.rootCause(),
        report.resolution(),
        report.prevention(),
        symptoms,
        resolutionSteps,
        tags.stream().filter(item -> !item.isBlank()).toList());
  }

  private void addTag(LinkedHashSet<String> tags, String value) {
    String tag = normalizeTag(value);
    if (!tag.isBlank()) {
      tags.add(tag);
    }
  }

  private String normalizeTag(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }

    String normalized =
        value
            .trim()
            .toLowerCase()
            .replaceAll("[^a-z0-9\\u4e00-\\u9fa5]+", "-")
            .replaceAll("^-+", "")
            .replaceAll("-+$", "");

    return normalized.length() > 64 ? normalized.substring(0, 64) : normalized;
  }

  private String blankToFallback(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }
}
