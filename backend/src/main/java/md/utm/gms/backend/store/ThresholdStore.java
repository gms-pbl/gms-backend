package md.utm.gms.backend.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ThresholdStore {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ThresholdStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> get(String tenantId, String greenhouseId, String zoneId) {
        List<String> rows = jdbcTemplate.queryForList(
                """
                SELECT thresholds
                FROM gms.zone_threshold
                WHERE tenant_id = ? AND greenhouse_id = ? AND zone_id = ?
                """,
                String.class,
                tenantId, greenhouseId, zoneId
        );

        if (rows.isEmpty()) {
            return new LinkedHashMap<>();
        }
        return fromJson(rows.get(0));
    }

    public Map<String, Object> put(String tenantId, String greenhouseId, String zoneId,
                                   Map<String, Object> thresholds) {
        String json = toJson(thresholds);
        jdbcTemplate.update(
                """
                INSERT INTO gms.zone_threshold (tenant_id, greenhouse_id, zone_id, thresholds, updated_at)
                VALUES (?, ?, ?, ?::jsonb, NOW())
                ON CONFLICT (tenant_id, greenhouse_id, zone_id)
                DO UPDATE SET thresholds = EXCLUDED.thresholds, updated_at = NOW()
                """,
                tenantId, greenhouseId, zoneId, json
        );
        return thresholds;
    }

    private Map<String, Object> fromJson(String json) {
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException e) {
            return new LinkedHashMap<>();
        }
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
