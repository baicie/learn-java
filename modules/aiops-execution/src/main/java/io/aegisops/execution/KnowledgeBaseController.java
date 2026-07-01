package io.aegisops.execution;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.execution.dto.KnowledgeBaseIndexResponse;
import io.aegisops.execution.dto.KnowledgeBaseSearchRequest;
import io.aegisops.execution.dto.KnowledgeBaseSearchResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasAuthority('incident:read')")
public class KnowledgeBaseController {
  private final KnowledgeBaseIndexService indexService;
  private final KnowledgeBaseSearchService searchService;

  public KnowledgeBaseController(
      KnowledgeBaseIndexService indexService, KnowledgeBaseSearchService searchService) {
    this.indexService = indexService;
    this.searchService = searchService;
  }

  @PostMapping("/api/incident-cases/{caseId}/kb/index")
  public ApiResponse<KnowledgeBaseIndexResponse> indexIncidentCase(@PathVariable String caseId) {
    return ApiResponse.ok(indexService.indexIncidentCase(TenantContext.requireTenantId(), caseId));
  }

  @GetMapping("/api/knowledge-base/documents/{documentId}")
  public ApiResponse<KnowledgeBaseIndexResponse> getDocument(@PathVariable String documentId) {
    return ApiResponse.ok(indexService.getDocument(TenantContext.requireTenantId(), documentId));
  }

  @GetMapping("/api/knowledge-base/documents/source/{sourceType}/{sourceId}")
  public ApiResponse<KnowledgeBaseIndexResponse> getDocumentBySource(
      @PathVariable String sourceType, @PathVariable String sourceId) {
    return ApiResponse.ok(
        indexService.getDocumentBySource(TenantContext.requireTenantId(), sourceType, sourceId));
  }

  @PostMapping("/api/knowledge-base/search")
  public ApiResponse<KnowledgeBaseSearchResponse> search(
      @RequestBody KnowledgeBaseSearchRequest request) {
    return ApiResponse.ok(searchService.search(TenantContext.requireTenantId(), request));
  }
}
