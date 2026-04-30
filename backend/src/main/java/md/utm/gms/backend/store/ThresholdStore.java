package md.utm.gms.backend.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import md.utm.gms.backend.api.dto.SensorThreshold;
import md.utm.gms.backend.api.dto.ThresholdBounds;
import md.utm.gms.backend.api.dto.ThresholdConfigResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ThresholdStore {

    private static final TypeReference<LinkedHashMap<String, SensorThreshold>> THRESHOLD_TYPE = new TypeReference<>() {};

    public static final Set<String> SENSOR_KEYS = Set.of(
            "air_temp",
            "air_hum",
            "soil_moist",
            "soil_temp",
            "soil_cond",
            "soil_ph",
            "soil_n",
            "soil_p",
            "soil_k",
            "soil_salinity",
            "soil_tds"
    );

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ThresholdStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public ThresholdConfigResponse get(String tenantId, String greenhouseId, String zoneId) {
        List<ThresholdConfigResponse> rows = jdbcTemplate.query(
                """
                SELECT thresholds,
                       config_version,
                       last_command_id,
                       updated_at
                FROM gms.zone_threshold
                WHERE tenant_id = ? AND greenhouse_id = ? AND zone_id = ?
                """,
                (rs, rowNum) -> ThresholdConfigResponse.builder()
                        .tenantId(tenantId)
                        .greenhouseId(greenhouseId)
                        .zoneId(zoneId)
                        .thresholds(mergeWithDefaults(fromJson(rs.getString("thresholds"))))
                        .configVersion(rs.getLong("config_version"))
                        .commandId(rs.getString("last_command_id"))
                        .updatedAt(toInstant(rs.getTimestamp("updated_at")))
                        .build(),
                tenantId, greenhouseId, zoneId
        );

        if (rows.isEmpty()) {
            return ThresholdConfigResponse.builder()
                    .tenantId(tenantId)
                    .greenhouseId(greenhouseId)
                    .zoneId(zoneId)
                    .configVersion(0)
                    .thresholds(defaultThresholds())
                    .build();
        }

        return rows.get(0);
    }

    public ThresholdConfigResponse put(String tenantId,
                                       String greenhouseId,
                                       String zoneId,
                                       Map<String, SensorThreshold> thresholds,
                                       String updatedBy,
                                       String commandId) {
        LinkedHashMap<String, SensorThreshold> normalized = normalizeAndValidate(thresholds);
        String json = toJson(normalized);

        List<ThresholdConfigResponse> rows = jdbcTemplate.query(
                """
                INSERT INTO gms.zone_threshold(
                    tenant_id,
                    greenhouse_id,
                    zone_id,
                    thresholds,
                    config_version,
                    updated_by,
                    last_command_id,
                    updated_at
                ) VALUES (?, ?, ?, ?::jsonb, 1, ?, ?, NOW())
                ON CONFLICT (tenant_id, greenhouse_id, zone_id)
                DO UPDATE SET
                    thresholds = EXCLUDED.thresholds,
                    config_version = gms.zone_threshold.config_version + 1,
                    updated_by = EXCLUDED.updated_by,
                    last_command_id = EXCLUDED.last_command_id,
                    updated_at = NOW()
                RETURNING thresholds,
                          config_version,
                          last_command_id,
                          updated_at
                """,
                (rs, rowNum) -> ThresholdConfigResponse.builder()
                        .tenantId(tenantId)
                        .greenhouseId(greenhouseId)
                        .zoneId(zoneId)
                        .thresholds(mergeWithDefaults(fromJson(rs.getString("thresholds"))))
                        .configVersion(rs.getLong("config_version"))
                        .commandId(rs.getString("last_command_id"))
                        .updatedAt(toInstant(rs.getTimestamp("updated_at")))
                        .build(),
                tenantId,
                greenhouseId,
                zoneId,
                json,
                blankToNull(updatedBy),
                commandId
        );

        if (rows.isEmpty()) {
            throw new IllegalStateException("Threshold update did not return a row.");
        }

        return rows.get(0);
    }

    public LinkedHashMap<String, SensorThreshold> normalizeAndValidate(Map<String, SensorThreshold> thresholds) {
        if (thresholds == null || thresholds.isEmpty()) {
            throw new IllegalArgumentException("thresholds must contain at least one sensor config");
        }

        LinkedHashMap<String, SensorThreshold> normalized = defaultThresholds();
        for (Map.Entry<String, SensorThreshold> entry : thresholds.entrySet()) {
            String sensorKey = entry.getKey();
            if (!SENSOR_KEYS.contains(sensorKey)) {
                throw new IllegalArgumentException("Unsupported sensor key: " + sensorKey);
            }

            SensorThreshold sensorThreshold = normalizeSensorThreshold(entry.getValue());
            validateSensorThreshold(sensorKey, sensorThreshold);
            normalized.put(sensorKey, sensorThreshold);
        }

        return normalized;
    }

    public LinkedHashMap<String, SensorThreshold> defaultThresholds() {
        LinkedHashMap<String, SensorThreshold> defaults = new LinkedHashMap<>();
        defaults.put("air_temp", levels(18, 28, 12, 34, 5, 40));
        defaults.put("air_hum", levels(50, 75, 35, 85, 20, 95));
        defaults.put("soil_moist", levels(40, 70, 25, 80, 10, 90));
        defaults.put("soil_temp", levels(15, 25, 10, 30, 5, 35));
        defaults.put("soil_cond", levels(0.8, 2.5, 0.5, 3.5, 0.2, 5.0));
        defaults.put("soil_ph", levels(6.0, 7.0, 5.5, 7.5, 5.0, 8.0));
        defaults.put("soil_n", levels(100, 200, 50, 250, 20, 300));
        defaults.put("soil_p", levels(15, 40, 8, 60, 3, 80));
        defaults.put("soil_k", levels(150, 300, 80, 400, 40, 500));
        defaults.put("soil_salinity", levels(0, 2.0, 0, 4.0, 0, 6.0));
        defaults.put("soil_tds", levels(0, 1200, 0, 1800, 0, 2400));
        return defaults;
    }

    private LinkedHashMap<String, SensorThreshold> mergeWithDefaults(Map<String, SensorThreshold> saved) {
        LinkedHashMap<String, SensorThreshold> merged = defaultThresholds();
        if (saved == null || saved.isEmpty()) {
            return merged;
        }

        for (Map.Entry<String, SensorThreshold> entry : saved.entrySet()) {
            if (SENSOR_KEYS.contains(entry.getKey())) {
                merged.put(entry.getKey(), normalizeSensorThreshold(entry.getValue()));
            }
        }
        return merged;
    }

    private LinkedHashMap<String, SensorThreshold> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }

        try {
            return objectMapper.readValue(json, THRESHOLD_TYPE);
        } catch (JsonProcessingException e) {
            return new LinkedHashMap<>();
        }
    }

    private String toJson(Map<String, SensorThreshold> thresholds) {
        try {
            return objectMapper.writeValueAsString(thresholds);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Threshold serialisation failed", e);
        }
    }

    private static SensorThreshold levels(
            double normalMin, double normalMax,
            double warnMin, double warnMax,
            double critMin, double critMax) {
        return SensorThreshold.builder()
                .normal(bounds(normalMin, normalMax))
                .warn(bounds(warnMin, warnMax))
                .critical(bounds(critMin, critMax))
                .build();
    }

    private static ThresholdBounds bounds(Double min, Double max) {
        return ThresholdBounds.builder().min(min).max(max).build();
    }

    private static SensorThreshold normalizeSensorThreshold(SensorThreshold value) {
        if (value == null) {
            return SensorThreshold.builder()
                    .normal(new ThresholdBounds())
                    .warn(new ThresholdBounds())
                    .critical(new ThresholdBounds())
                    .build();
        }
        return SensorThreshold.builder()
                .normal(normalizeBounds(value.getNormal()))
                .warn(normalizeBounds(value.getWarn()))
                .critical(normalizeBounds(value.getCritical()))
                .build();
    }

    private static ThresholdBounds normalizeBounds(ThresholdBounds value) {
        if (value == null) {
            return new ThresholdBounds();
        }
        return ThresholdBounds.builder()
                .min(value.getMin())
                .max(value.getMax())
                .build();
    }

    private static void validateSensorThreshold(String sensorKey, SensorThreshold threshold) {
        validateBounds(sensorKey, "normal", threshold.getNormal());
        validateBounds(sensorKey, "warn", threshold.getWarn());
        validateBounds(sensorKey, "critical", threshold.getCritical());

        ThresholdBounds normal = threshold.getNormal();
        ThresholdBounds warn = threshold.getWarn();
        ThresholdBounds critical = threshold.getCritical();

        requireMinAtOrBelow(sensorKey, "warn.min", warn.getMin(), "normal.min", normal.getMin());
        requireMaxAtOrAbove(sensorKey, "warn.max", warn.getMax(), "normal.max", normal.getMax());
        requireMinAtOrBelow(sensorKey, "critical.min", critical.getMin(), "warn.min", warn.getMin());
        requireMaxAtOrAbove(sensorKey, "critical.max", critical.getMax(), "warn.max", warn.getMax());
    }

    private static void validateBounds(String sensorKey, String level, ThresholdBounds bounds) {
        Double min = bounds.getMin();
        Double max = bounds.getMax();
        if (min != null && !Double.isFinite(min)) {
            throw new IllegalArgumentException(sensorKey + "." + level + ".min must be finite");
        }
        if (max != null && !Double.isFinite(max)) {
            throw new IllegalArgumentException(sensorKey + "." + level + ".max must be finite");
        }
        if (min != null && max != null && min > max) {
            throw new IllegalArgumentException(sensorKey + "." + level + ".min must be <= max");
        }
    }

    private static void requireMinAtOrBelow(String sensorKey,
                                            String leftLabel,
                                            Double left,
                                            String rightLabel,
                                            Double right) {
        if (left != null && right != null && left > right) {
            throw new IllegalArgumentException(sensorKey + ": " + leftLabel + " must be <= " + rightLabel);
        }
    }

    private static void requireMaxAtOrAbove(String sensorKey,
                                            String leftLabel,
                                            Double left,
                                            String rightLabel,
                                            Double right) {
        if (left != null && right != null && left < right) {
            throw new IllegalArgumentException(sensorKey + ": " + leftLabel + " must be >= " + rightLabel);
        }
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
