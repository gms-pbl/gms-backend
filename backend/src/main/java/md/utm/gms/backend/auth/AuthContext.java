package md.utm.gms.backend.auth;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

public final class AuthContext {

    private AuthContext() {
    }

    public static AuthenticatedUser requireUser(Authentication authentication) {
        if (authentication == null) {
            throw new AccessDeniedException("Missing authentication context");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof AuthenticatedUser user) {
            return user;
        }

        throw new AccessDeniedException("Unsupported authentication principal");
    }

    public static String requireTenantId(Authentication authentication) {
        String tenantId = requireUser(authentication).getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new AccessDeniedException("Authenticated user is not linked to a tenant");
        }
        return tenantId;
    }
}
