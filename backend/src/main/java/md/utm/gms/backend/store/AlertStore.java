package md.utm.gms.backend.store;

import md.utm.gms.backend.api.dto.AlertResponse;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store for active alerts.
 *
 * <p>Keyed by alert id. Alerts survive until explicitly dismissed via the REST API.
 * Sorting mirrors the frontend: unacknowledged first, then CRITICAL → WARNING → INFO.
 *
 * <p>This store will be backed by InfluxDB / PostgreSQL once the persistence
 * work-package is implemented. The controller interface remains unchanged.
 */
@Component
public class AlertStore {

    private static final Comparator<AlertResponse> SORT_ORDER = Comparator
            .comparingInt((AlertResponse a) -> a.isAcknowledged() ? 1 : 0)
            .thenComparingInt(a -> severityRank(a.getSeverity()));

    private final ConcurrentHashMap<String, AlertResponse> alerts = new ConcurrentHashMap<>();

    public void add(AlertResponse alert) {
        alerts.put(alert.getId(), alert);
    }

    public Optional<AlertResponse> acknowledge(String id) {
        AlertResponse alert = alerts.get(id);
        if (alert == null) return Optional.empty();
        alert.setAcknowledged(true);
        return Optional.of(alert);
    }

    public boolean dismiss(String id) {
        return alerts.remove(id) != null;
    }

    public List<AlertResponse> getAll() {
        return alerts.values().stream().sorted(SORT_ORDER).toList();
    }

    private static int severityRank(String severity) {
        return switch (severity != null ? severity : "") {
            case "CRITICAL" -> 0;
            case "WARNING"  -> 1;
            case "INFO"     -> 2;
            default         -> 3;
        };
    }
}
