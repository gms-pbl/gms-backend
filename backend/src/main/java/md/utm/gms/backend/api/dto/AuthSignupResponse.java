package md.utm.gms.backend.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import md.utm.gms.backend.auth.AuthService;

public class AuthSignupResponse {

    @JsonProperty("user_id")
    private long userId;
    private String username;

    @JsonProperty("tenant_id")
    private String tenantId;

    @JsonProperty("tenant_name")
    private String tenantName;

    private String role;

    public AuthSignupResponse() {
    }

    public AuthSignupResponse(long userId, String username, String tenantId, String tenantName, String role) {
        this.userId = userId;
        this.username = username;
        this.tenantId = tenantId;
        this.tenantName = tenantName;
        this.role = role;
    }

    public static AuthSignupResponse from(AuthService.SignupResult result) {
        return new AuthSignupResponse(
                result.userId(),
                result.username(),
                result.tenantId(),
                result.tenantName(),
                result.role()
        );
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getTenantName() {
        return tenantName;
    }

    public void setTenantName(String tenantName) {
        this.tenantName = tenantName;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
