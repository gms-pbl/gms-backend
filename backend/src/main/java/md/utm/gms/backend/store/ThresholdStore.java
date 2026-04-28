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

    /**
     * Returned when a zone has no saved thresholds yet.
     * Values are typical ranges for a temperate vegetable greenhouse.
     */
    private static final Map<String, Object> DEFAULTS = Map.of(
        "air_temperature", levels( 18,  28,  12,  34,   5,  40),
        "air_humidity",    levels( 50,  75,  35,  85,  20,  95),
        "soil_moisture",   levels( 40,  70,  25,  80,  10,  90),
        "soil_temp",       levels( 15,  25,  10,  30,   5,  35),
        "soil_ph",         levels(6.0, 7.0, 5.5, 7.5, 5.0, 8.0),
        "soil_ec",         levels(0.8, 2.5, 0.5, 3.5, 0.2, 5.0),
        "soil_nitrogen",   levels(100, 200,  50, 250,  20, 300),
        "soil_phosphorus", levels( 15,  40,   8,  60,   3,  80),
        "soil_potassium",  levels(150, 300,  80, 400,  40, 500),
        "soil_salinity",   levels(  0, 2.0,   0, 4.0,   0, 6.0)
    );

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
            return DEFAULTS;
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

    private static Map<String, Object> levels(
            double normalMin, double normalMax,
            double warnMin,   double warnMax,
            double critMin,   double critMax) {
        return Map.of(
            "normal",   Map.of("min", normalMin, "max", normalMax),
            "warn",     Map.of("min", warnMin,   "max", warnMax),
            "critical", Map.of("min", critMin,   "max", critMax)
        );
    }
}
