# Zones MQTT Contract v1

This contract defines the current backend <-> gateway integration for zone management.

Identity hierarchy:

`tenant -> greenhouse (= gateway) -> zone (= Portenta) -> actuator/sensor`

## Topic Tree

- Uplink (gateway -> backend): `gms/{tenant_id}/{greenhouse_id}/uplink/{stream}`
- Downlink (backend -> gateway): `gms/{tenant_id}/{greenhouse_id}/downlink/{stream}`

## Stream Meaning

### Uplink streams

- `telemetry` - gateway-normalized metrics used by dashboard/analytics.
- `registry` - discovery and assignment lifecycle events for zone management.
- `status` - gateway heartbeat and connectivity state.
- `command_ack` - command correlation and execution status.

### Downlink streams

- `registry` - assignment operations (`ASSIGN_ZONE`, `UNASSIGN_ZONE`, `ZONE_REGISTRY_SYNC`).
- `command` - semantic action requests to a specific zone/device.

## Direction and Responsibility

| Topic family | Publisher | Consumer | Why it exists |
|---|---|---|---|
| `.../uplink/telemetry` | gateway | backend | deliver live metrics |
| `.../uplink/registry` | gateway | backend | keep discovered/assigned state current |
| `.../uplink/status` | gateway | backend | monitor gateway availability |
| `.../uplink/command_ack` | gateway | backend | close command loop with status |
| `.../downlink/registry` | backend | gateway | apply zone registry changes |
| `.../downlink/command` | backend | gateway | execute device-level action |

Supported uplink streams:

- `telemetry`
- `registry`
- `status`
- `command_ack`

Supported downlink streams:

- `registry`
- `command`

## Registry Discovery (uplink/registry)

```json
{
  "event_id": "2de437d7-a0fd-4748-a40c-e3653dfc43d5",
  "type": "DEVICE_DISCOVERED",
  "tenant_id": "tenant-demo",
  "greenhouse_id": "greenhouse-demo",
  "gateway_id": "greenhouse-demo",
  "device_id": "6fd17fb0-7a0f-45c7-b22c-3da2be0ae8db",
  "timestamp": "2026-04-16T12:00:00Z",
  "metadata": {
    "label": "Virtual Portenta 1"
  }
}
```

## Assign / Unassign Zone (downlink/registry)

```json
{
  "command_id": "af05db36-8c75-4bbf-815b-5191906e96e3",
  "type": "ASSIGN_ZONE",
  "tenant_id": "tenant-demo",
  "greenhouse_id": "greenhouse-demo",
  "gateway_id": "greenhouse-demo",
  "device_id": "6fd17fb0-7a0f-45c7-b22c-3da2be0ae8db",
  "zone_id": "2ec9c8c5-d69e-4277-88f3-d429fdad067e",
  "zone_name": "Tomato West",
  "issued_at": "2026-04-16T12:01:00Z"
}
```

```json
{
  "command_id": "5dc07d1d-fd9f-4b2e-bf17-f354416f8a43",
  "type": "UNASSIGN_ZONE",
  "tenant_id": "tenant-demo",
  "greenhouse_id": "greenhouse-demo",
  "gateway_id": "greenhouse-demo",
  "device_id": "6fd17fb0-7a0f-45c7-b22c-3da2be0ae8db",
  "issued_at": "2026-04-16T12:04:00Z"
}
```

## Full Registry Sync (downlink/registry)

```json
{
  "command_id": "6a6ef8f6-4e5f-4ba7-b6e8-52a82afb7f28",
  "type": "ZONE_REGISTRY_SYNC",
  "tenant_id": "tenant-demo",
  "greenhouse_id": "greenhouse-demo",
  "gateway_id": "greenhouse-demo",
  "config_version": "2026-04-16T12:10:00Z",
  "issued_at": "2026-04-16T12:10:00Z",
  "zones": [
    {
      "zone_id": "2ec9c8c5-d69e-4277-88f3-d429fdad067e",
      "zone_name": "Tomato West",
      "device_id": "6fd17fb0-7a0f-45c7-b22c-3da2be0ae8db"
    },
    {
      "zone_id": "f2571ec0-2538-4ba6-a8dc-a66da97f3c58",
      "zone_name": "Cucumber East",
      "device_id": "d31f0f43-a65d-4840-878e-42e711326f20"
    }
  ]
}
```

## Gateway Telemetry (uplink/telemetry)

```json
{
  "event_id": "89549987-7df0-4d14-bb95-7295948bd2ec",
  "tenant_id": "tenant-demo",
  "greenhouse_id": "greenhouse-demo",
  "gateway_id": "greenhouse-demo",
  "zone_id": "2ec9c8c5-d69e-4277-88f3-d429fdad067e",
  "device_id": "6fd17fb0-7a0f-45c7-b22c-3da2be0ae8db",
  "kind": "delta",
  "timestamp": "2026-04-16T12:11:00Z",
  "metrics": {
    "soil_moist": 33.8,
    "air_temp": 24.1,
    "din_00": 1,
    "dout_00": 0
  }
}
```

Notes:

- binary IO values are represented as numbers (`0` or `1`)
- `din_*` and `dout_*` keys are treated like normal metrics in telemetry stream

## Command Request + Ack

Downlink command (`downlink/command`) is semantic and device-scoped:

```json
{
  "command_id": "f5314332-d648-4720-a149-894f97774606",
  "type": "IRRIGATION_ON",
  "tenant_id": "tenant-demo",
  "greenhouse_id": "greenhouse-demo",
  "gateway_id": "greenhouse-demo",
  "device_id": "6fd17fb0-7a0f-45c7-b22c-3da2be0ae8db",
  "issued_at": "2026-04-16T12:15:00Z"
}
```

Gateway ack (`uplink/command_ack`) returns result/correlation:

```json
{
  "event_id": "2434fe26-4431-4244-a6a8-6df96c694e10",
  "type": "COMMAND_ACK",
  "tenant_id": "tenant-demo",
  "greenhouse_id": "greenhouse-demo",
  "gateway_id": "greenhouse-demo",
  "command_id": "f5314332-d648-4720-a149-894f97774606",
  "device_id": "6fd17fb0-7a0f-45c7-b22c-3da2be0ae8db",
  "zone_id": "2ec9c8c5-d69e-4277-88f3-d429fdad067e",
  "status": "FORWARDED",
  "timestamp": "2026-04-16T12:15:01Z"
}
```

### Direct output command via payload

The gateway also accepts explicit low-level output payloads:

```json
{
  "command_id": "30b0807d-8d54-4e65-9a38-7248ad494f68",
  "type": "SET_OUTPUT",
  "tenant_id": "tenant-demo",
  "greenhouse_id": "greenhouse-demo",
  "device_id": "6fd17fb0-7a0f-45c7-b22c-3da2be0ae8db",
  "payload": {
    "channel": 0,
    "state": 1
  },
  "issued_at": "2026-04-16T12:20:00Z"
}
```

## End-to-End Sequence Example

1. Device announces locally to gateway (`edge ... registry announce`).
2. Gateway publishes `DEVICE_DISCOVERED` to `gms ... uplink registry`.
3. Backend exposes device in `GET /v1/zones/registry` as discovered.
4. Frontend assigns zone via `POST /v1/zones/assign`.
5. Backend sends `ASSIGN_ZONE` on `gms ... downlink registry`.
6. Gateway applies local mapping and publishes local `edge ... config` to device.
7. Device starts reporting telemetry; gateway forwards to `gms ... uplink telemetry`.
8. Operator sends command via `POST /v1/zones/command`.
9. Backend sends downlink command; gateway translates/forwards to local output topic.
10. Gateway publishes `COMMAND_ACK` to `gms ... uplink command_ack`.
