package io.aegisops.platform.dictionary;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.security.UserPrincipal;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/dictionaries")
public class DictionaryController {
  private final DictionaryService service;

  public DictionaryController(DictionaryService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('platform:dict:read')")
  public ApiResponse<List<DictTypeRecord>> listTypes() {
    return ApiResponse.ok(service.listTypes(TenantContext.requireTenantId()));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('platform:dict:write')")
  public ApiResponse<DictTypeRecord> createType(
      @RequestBody CreateDictTypeRequest request, @AuthenticationPrincipal UserPrincipal user) {
    return ApiResponse.ok(
        service.createType(
            TenantContext.requireTenantId(), request, user == null ? "system" : user.id()));
  }

  @PutMapping("/{dictCode}")
  @PreAuthorize("hasAuthority('platform:dict:write')")
  public ApiResponse<DictTypeRecord> updateType(
      @PathVariable String dictCode,
      @RequestBody UpdateDictTypeRequest request,
      @AuthenticationPrincipal UserPrincipal user) {
    return ApiResponse.ok(
        service.updateType(
            TenantContext.requireTenantId(),
            dictCode,
            request,
            user == null ? "system" : user.id()));
  }

  @GetMapping("/{dictCode}/items")
  @PreAuthorize("hasAuthority('platform:dict:read')")
  public ApiResponse<List<DictItemRecord>> listItems(@PathVariable String dictCode) {
    return ApiResponse.ok(service.listItems(TenantContext.requireTenantId(), dictCode));
  }

  @PostMapping("/{dictCode}/items")
  @PreAuthorize("hasAuthority('platform:dict:write')")
  public ApiResponse<DictItemRecord> createItem(
      @PathVariable String dictCode,
      @RequestBody CreateDictItemRequest request,
      @AuthenticationPrincipal UserPrincipal user) {
    return ApiResponse.ok(
        service.createItem(
            TenantContext.requireTenantId(),
            dictCode,
            request,
            user == null ? "system" : user.id()));
  }

  @PutMapping("/{dictCode}/items/{itemId}")
  @PreAuthorize("hasAuthority('platform:dict:write')")
  public ApiResponse<DictItemRecord> updateItem(
      @PathVariable String dictCode,
      @PathVariable String itemId,
      @RequestBody UpdateDictItemRequest request,
      @AuthenticationPrincipal UserPrincipal user) {
    return ApiResponse.ok(
        service.updateItem(
            TenantContext.requireTenantId(),
            dictCode,
            itemId,
            request,
            user == null ? "system" : user.id()));
  }
}
