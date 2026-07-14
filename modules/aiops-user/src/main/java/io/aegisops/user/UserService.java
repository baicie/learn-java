package io.aegisops.user;

import io.aegisops.common.exception.AppException;
import io.aegisops.common.exception.NotFoundException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {
  private final UserRepository repository;
  private final PasswordEncoder passwordEncoder;

  public UserService(UserRepository repository, PasswordEncoder passwordEncoder) {
    this.repository = repository;
    this.passwordEncoder = passwordEncoder;
  }

  public List<UserAccount> listByTenant(String tenantId) {
    return repository.findAllByTenantId(tenantId);
  }

  public Map<String, String> displayNames(String tenantId, Collection<String> userIds) {
    return repository.findDisplayNamesByTenantIdAndIds(tenantId, userIds);
  }

  public Optional<UserAccount> findByUsername(String username) {
    return repository.findByUsername(username);
  }

  public UserAccount getByUsername(String username) {
    return repository
        .findByUsername(username)
        .orElseThrow(() -> new NotFoundException("User not found: " + username));
  }

  public UserAccount getById(String id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new NotFoundException("User not found: " + id));
  }

  public boolean passwordMatches(String raw, UserAccount user) {
    return passwordEncoder.matches(raw, user.passwordHash());
  }

  public UserAccount updateProfile(String id, String displayName, String email) {
    return repository.updateProfile(id, displayName, email);
  }

  public void changePassword(String id, String currentPassword, String newPassword) {
    UserAccount user = getById(id);
    if (!passwordMatches(currentPassword, user)) {
      throw new AppException("INVALID_CURRENT_PASSWORD", "Current password is incorrect");
    }
    if (newPassword == null || newPassword.length() < 8) {
      throw new AppException("PASSWORD_TOO_SHORT", "Password must be at least 8 characters");
    }
    repository.updatePassword(id, passwordEncoder.encode(newPassword));
  }

  public UserAccount createAdminIfAbsent(String tenantId, String username, String rawPassword) {
    UserAccount admin =
        repository
            .findByUsername(username)
            .orElseGet(
                () ->
                    repository.create(
                        tenantId,
                        username,
                        "Admin",
                        "admin@local",
                        passwordEncoder.encode(rawPassword)));

    repository.ensureLegacyRole("admin", "Administrator");
    repository.attachLegacyRole(admin.id(), "admin");

    return admin;
  }
}
