package io.aegisops.tenant;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TenantService {
    private final TenantRepository repository;

    public TenantService(TenantRepository repository) {
        this.repository = repository;
    }

    public List<Tenant> list() {
        return repository.findAll();
    }

    public Tenant getOrCreateDefaultTenant() {
        return repository.findByCode("default").orElseGet(() -> repository.create("default", "Default Tenant"));
    }
}
