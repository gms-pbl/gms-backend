package md.utm.gms.backend.store;

import md.utm.gms.backend.api.dto.GreenhouseResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
public class GreenhouseStore {

    private static final RowMapper<GreenhouseResponse> GREENHOUSE_MAPPER = (rs, rowNum) -> {
        double latRaw = rs.getDouble("latitude");
        Double lat = rs.wasNull() ? null : latRaw;
        double lonRaw = rs.getDouble("longitude");
        Double lon = rs.wasNull() ? null : lonRaw;
        return new GreenhouseResponse(
                rs.getString("tenant_id"),
                rs.getString("greenhouse_id"),
                rs.getString("gateway_id"),
                rs.getString("name"),
                lat,
                lon,
                rs.getString("address"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    };

    private final JdbcTemplate jdbcTemplate;

    public GreenhouseStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<GreenhouseResponse> listByTenant(String tenantId) {
        return jdbcTemplate.query(
                """
                SELECT tenant_id, greenhouse_id, gateway_id, name, latitude, longitude, address, created_at, updated_at
                FROM gms.greenhouse
                WHERE tenant_id = ?
                ORDER BY created_at ASC
                """,
                GREENHOUSE_MAPPER,
                tenantId
        );
    }

    public Optional<GreenhouseResponse> find(String tenantId, String greenhouseId) {
        List<GreenhouseResponse> matches = jdbcTemplate.query(
                """
                SELECT tenant_id, greenhouse_id, gateway_id, name, latitude, longitude, address, created_at, updated_at
                FROM gms.greenhouse
                WHERE tenant_id = ? AND greenhouse_id = ?
                """,
                GREENHOUSE_MAPPER,
                tenantId,
                greenhouseId
        );

        if (matches.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(matches.get(0));
    }

    public boolean exists(String tenantId, String greenhouseId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM gms.greenhouse WHERE tenant_id = ? AND greenhouse_id = ?",
                Integer.class,
                tenantId,
                greenhouseId
        );
        return count != null && count > 0;
    }

    public GreenhouseResponse create(String tenantId,
                                     String greenhouseId,
                                     String gatewayId,
                                     String name,
                                     Double latitude,
                                     Double longitude,
                                     String address) {
        jdbcTemplate.update(
                """
                INSERT INTO gms.greenhouse(tenant_id, greenhouse_id, gateway_id, name, latitude, longitude, address)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                tenantId,
                greenhouseId,
                gatewayId,
                name,
                latitude,
                longitude,
                blankToNull(address)
        );

        return find(tenantId, greenhouseId)
                .orElseThrow(() -> new IllegalStateException("Created greenhouse could not be reloaded."));
    }

    public Optional<GreenhouseResponse> update(String tenantId,
                                               String greenhouseId,
                                               String name,
                                               String gatewayId,
                                               Double latitude,
                                               Double longitude,
                                               String address) {
        int updated = jdbcTemplate.update(
                """
                UPDATE gms.greenhouse
                SET name = COALESCE(?, name),
                    gateway_id = COALESCE(?, gateway_id),
                    latitude = COALESCE(?, latitude),
                    longitude = COALESCE(?, longitude),
                    address = COALESCE(?, address),
                    updated_at = NOW()
                WHERE tenant_id = ? AND greenhouse_id = ?
                """,
                blankToNull(name),
                blankToNull(gatewayId),
                latitude,
                longitude,
                blankToNull(address),
                tenantId,
                greenhouseId
        );

        if (updated == 0) {
            return Optional.empty();
        }

        return find(tenantId, greenhouseId);
    }

    @Transactional
    public boolean deleteHard(String tenantId, String greenhouseId) {
        int deleted = jdbcTemplate.update(
                "DELETE FROM gms.greenhouse WHERE tenant_id = ? AND greenhouse_id = ?",
                tenantId,
                greenhouseId
        );

        if (deleted == 0) {
            return false;
        }

        jdbcTemplate.update(
                "DELETE FROM gms.zone_device WHERE tenant_id = ? AND greenhouse_id = ?",
                tenantId,
                greenhouseId
        );
        jdbcTemplate.update(
                "DELETE FROM gms.command_ack WHERE tenant_id = ? AND greenhouse_id = ?",
                tenantId,
                greenhouseId
        );
        jdbcTemplate.update(
                "DELETE FROM gms.threshold_apply_status WHERE tenant_id = ? AND greenhouse_id = ?",
                tenantId,
                greenhouseId
        );
        jdbcTemplate.update(
                "DELETE FROM gms.zone_threshold WHERE tenant_id = ? AND greenhouse_id = ?",
                tenantId,
                greenhouseId
        );
        jdbcTemplate.update(
                "DELETE FROM gms.latest_metric WHERE tenant_id = ? AND greenhouse_id = ?",
                tenantId,
                greenhouseId
        );
        jdbcTemplate.update(
                "DELETE FROM gms.telemetry_reading WHERE tenant_id = ? AND greenhouse_id = ?",
                tenantId,
                greenhouseId
        );
        jdbcTemplate.update(
                "DELETE FROM gms.alert_event WHERE tenant_id = ? AND greenhouse_id = ?",
                tenantId,
                greenhouseId
        );

        return true;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
