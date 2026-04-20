package md.utm.gms.backend.auth;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public AuthService(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public SignupResult signupTenantAdmin(String username,
                                          String rawPassword,
                                          String tenantName,
                                          String requestedTenantId) {
        String normalizedUsername = normalizeUsername(username);
        String normalizedTenantName = normalizeTenantName(tenantName);
        String tenantId = resolveTenantId(normalizedTenantName, requestedTenantId);

        if (rawPassword == null || rawPassword.length() < 8) {
            throw new IllegalArgumentException("Password must contain at least 8 characters.");
        }

        if (usernameExists(normalizedUsername)) {
            throw new IllegalArgumentException("Username is already registered.");
        }

        if (tenantExists(tenantId)) {
            throw new IllegalArgumentException("Tenant id is already in use.");
        }

        String passwordHash = passwordEncoder.encode(rawPassword);

        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO gms.tenant(tenant_id, name)
                    VALUES (?, ?)
                    """,
                    tenantId,
                    normalizedTenantName
            );

            Long userId = jdbcTemplate.queryForObject(
                    """
                    INSERT INTO gms.app_user(username, password_hash, active)
                    VALUES (?, ?, TRUE)
                    RETURNING id
                    """,
                    Long.class,
                    normalizedUsername,
                    passwordHash
            );

            if (userId == null) {
                throw new IllegalStateException("User creation failed.");
            }

            jdbcTemplate.update(
                    """
                    INSERT INTO gms.tenant_membership(user_id, tenant_id, role)
                    VALUES (?, ?, 'ADMIN')
                    """,
                    userId,
                    tenantId
            );

            return new SignupResult(userId, normalizedUsername, tenantId, normalizedTenantName, "ADMIN");
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalArgumentException("Unable to create account with provided identifiers.");
        }
    }

    public AuthenticatedUser loadUserByUsername(String username) {
        String normalized = normalizeUsername(username);

        List<AuthenticatedUser> matches = jdbcTemplate.query(
                """
                SELECT u.id,
                       u.username,
                       u.password_hash,
                       u.active,
                       m.tenant_id,
                       m.role
                FROM gms.app_user u
                JOIN gms.tenant_membership m ON m.user_id = u.id
                WHERE LOWER(u.username) = LOWER(?)
                ORDER BY m.created_at ASC
                LIMIT 1
                """,
                (rs, rowNum) -> new AuthenticatedUser(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getString("password_hash"),
                        rs.getString("tenant_id"),
                        rs.getString("role"),
                        rs.getBoolean("active")
                ),
                normalized
        );

        if (matches.isEmpty()) {
            throw new UsernameNotFoundException("Invalid username or password.");
        }

        return matches.get(0);
    }

    public Optional<AuthProfile> findProfileByUserId(long userId) {
        List<AuthProfile> matches = jdbcTemplate.query(
                """
                SELECT u.id,
                       u.username,
                       u.active,
                       m.tenant_id,
                       m.role,
                       t.name AS tenant_name
                FROM gms.app_user u
                JOIN gms.tenant_membership m ON m.user_id = u.id
                JOIN gms.tenant t ON t.tenant_id = m.tenant_id
                WHERE u.id = ?
                ORDER BY m.created_at ASC
                LIMIT 1
                """,
                (rs, rowNum) -> new AuthProfile(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getString("tenant_id"),
                        rs.getString("tenant_name"),
                        rs.getString("role"),
                        rs.getBoolean("active")
                ),
                userId
        );

        if (matches.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(matches.get(0));
    }

    private boolean usernameExists(String username) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM gms.app_user WHERE LOWER(username) = LOWER(?)",
                Integer.class,
                username
        );
        return count != null && count > 0;
    }

    private boolean tenantExists(String tenantId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM gms.tenant WHERE tenant_id = ?",
                Integer.class,
                tenantId
        );
        return count != null && count > 0;
    }

    private String resolveTenantId(String tenantName, String requestedTenantId) {
        if (requestedTenantId != null && !requestedTenantId.isBlank()) {
            return sanitizeIdentifier(requestedTenantId);
        }

        String base = sanitizeIdentifier(tenantName);
        if (base.length() > 40) {
            base = base.substring(0, 40);
        }

        String candidate = base;
        while (tenantExists(candidate)) {
            candidate = base + "-" + UUID.randomUUID().toString().substring(0, 8);
        }
        return candidate;
    }

    private static String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username is required.");
        }
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeTenantName(String tenantName) {
        if (tenantName == null || tenantName.isBlank()) {
            throw new IllegalArgumentException("Tenant name is required.");
        }
        return tenantName.trim();
    }

    private static String sanitizeIdentifier(String value) {
        String sanitized = value
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");

        if (sanitized.isBlank()) {
            sanitized = "tenant-" + UUID.randomUUID().toString().substring(0, 8);
        }
        return sanitized;
    }

    public record SignupResult(long userId,
                               String username,
                               String tenantId,
                               String tenantName,
                               String role) {
    }

    public record AuthProfile(long userId,
                              String username,
                              String tenantId,
                              String tenantName,
                              String role,
                              boolean active) {
    }
}
