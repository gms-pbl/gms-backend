#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

WATCH_LOGS=false
UP_ARGS=()
LOG_SERVICES=()

for arg in "$@"; do
  case "$arg" in
    -v|--verbose)
      WATCH_LOGS=true
      ;;
    *)
      UP_ARGS+=("$arg")
      if [[ "$arg" != -* ]]; then
        LOG_SERVICES+=("$arg")
      fi
      ;;
  esac
done

cd "${INFRA_ROOT}"
docker compose up -d "${UP_ARGS[@]}"

if [[ "$WATCH_LOGS" == true ]]; then
  if [[ ${#LOG_SERVICES[@]} -gt 0 ]]; then
    exec docker compose logs -f --tail=120 "${LOG_SERVICES[@]}"
  fi
  exec docker compose logs -f --tail=120
fi
