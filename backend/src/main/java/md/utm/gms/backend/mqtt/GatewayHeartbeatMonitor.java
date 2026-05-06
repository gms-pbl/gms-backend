package md.utm.gms.backend.mqtt;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import md.utm.gms.backend.api.dto.AlertResponse;
import md.utm.gms.backend.store.AlertStore;
import md.utm.gms.backend.store.GatewayStatusStore;
import md.utm.gms.backend.store.GatewayStatusStore.OfflineGateway;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Periodically checks whether any gateway has stopped sending heartbeats and
 * raises a CRITICAL alert in {@link AlertStore} if it has.
 *
 * <p>A deterministic alert ID is used per gateway so repeated firings upsert
 * the same row rather than flooding the alert table. If an operator dismisses
 * the alert while the gateway is still offline, it will be re-raised on the
 * next scheduler tick.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GatewayHeartbeatMonitor {

    private static final Duration HEARTBEAT_TIMEOUT = Duration.ofMinutes(5);

    private final GatewayStatusStore gatewayStatusStore;
    private final AlertStore alertStore;

    @Scheduled(fixedDelay = 60_000)
    public void checkHeartbeats() {
        List<OfflineGateway> stale = gatewayStatusStore.findStaleSince(HEARTBEAT_TIMEOUT);

        for (OfflineGateway gw : stale) {
            long minutesAgo = Duration.between(gw.lastSeenAt(), Instant.now()).toMinutes();

            String alertId = "hb:" + gw.tenantId() + ":" + gw.greenhouseId() + ":" + gw.gatewayId();

            alertStore.add(AlertResponse.builder()
                    .id(alertId)
                    .tenantId(gw.tenantId())
                    .greenhouseId(gw.greenhouseId())
                    .gatewayId(gw.gatewayId())
                    .severity("CRITICAL")
                    .sensorKey("gateway_heartbeat")
                    .message("Gateway " + gw.gatewayId() + " has not sent a heartbeat in "
                            + minutesAgo + " minute(s). Last seen: " + gw.lastSeenAt() + ".")
                    .source("backend")
                    .triggeredAt(Instant.now())
                    .acknowledged(false)
                    .build());

            log.warn("Heartbeat miss  tenant={}  greenhouse={}  gateway={}  lastSeen={}  minutesAgo={}",
                    gw.tenantId(), gw.greenhouseId(), gw.gatewayId(), gw.lastSeenAt(), minutesAgo);
        }
    }
}
