package md.utm.gms.backend.auth;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class AuthenticatedUser implements UserDetails {

    private final long userId;
    private final String username;
    private final String passwordHash;
    private final String tenantId;
    private final String role;
    private final boolean active;

    public AuthenticatedUser(long userId,
                             String username,
                             String passwordHash,
                             String tenantId,
                             String role,
                             boolean active) {
        this.userId = userId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.tenantId = tenantId;
        this.role = role;
        this.active = active;
    }

    public long getUserId() {
        return userId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getRole() {
        return role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + normalizedRole()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return active;
    }

    @Override
    public boolean isAccountNonLocked() {
        return active;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return active;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    private String normalizedRole() {
        if (role == null || role.isBlank()) {
            return "ADMIN";
        }
        return role.trim().toUpperCase();
    }
}
