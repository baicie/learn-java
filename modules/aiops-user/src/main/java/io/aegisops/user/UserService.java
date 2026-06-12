package io.aegisops.user;

import io.aegisops.common.exception.NotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {
    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UserAccount> list() {
        return repository.findAll();
    }

    public UserAccount getByUsername(String username) {
        return repository.findByUsername(username).orElseThrow(() -> new NotFoundException("User not found: " + username));
    }

    public UserAccount getById(String id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("User not found: " + id));
    }

    public boolean passwordMatches(String raw, UserAccount user) {
        return passwordEncoder.matches(raw, user.passwordHash());
    }

    public UserAccount createAdminIfAbsent(String tenantId, String username, String rawPassword) {
        return repository.findByUsername(username).orElseGet(() -> {
            repository.ensureRole("admin", "Administrator");
            repository.ensureRole("operator", "Operator");
            UserAccount created = repository.create(tenantId, username, "Admin", "admin@local", passwordEncoder.encode(rawPassword));
            repository.attachRole(created.id(), "admin");
            return repository.findById(created.id()).orElseThrow();
        });
    }
}
