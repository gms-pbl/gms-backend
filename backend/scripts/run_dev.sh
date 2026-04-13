#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

if [[ -z "${JAVA_HOME:-}" ]]; then
  for candidate in "/usr/lib/jvm/java-21-openjdk" "/usr/lib/jvm/java-17-openjdk"; do
    if [[ -x "${candidate}/bin/java" && -x "${candidate}/bin/javac" ]]; then
      export JAVA_HOME="${candidate}"
      break
    fi
  done
fi

if [[ -n "${JAVA_HOME:-}" ]]; then
  export PATH="${JAVA_HOME}/bin:${PATH}"
fi

cd "${BACKEND_ROOT}"
exec mvn spring-boot:run -Dspring-boot.run.profiles=dev "$@"
