#!/bin/sh

set -eu

SCRIPT_DIR=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
# shellcheck source=common.sh
. "${SCRIPT_DIR}/common.sh"

require_command docker
require_command curl
validate_environment_file
compose config --quiet
compose ps

check_endpoint() {
  name=$1
  url=$2
  code=$(curl --silent --show-error --output /dev/null --write-out '%{http_code}' "${url}") \
    || die "${name} endpoint is unreachable"
  [ "${code}" = "200" ] || die "${name} endpoint returned HTTP ${code}"
  printf '%s: HTTP %s\n' "${name}" "${code}"
}

file_app_port=$(env_file_value FINGUARD_APP_PORT)
file_prometheus_port=$(env_file_value PROMETHEUS_PORT)
file_grafana_port=$(env_file_value GRAFANA_PORT)
app_port=${FINGUARD_APP_PORT:-${file_app_port:-8080}}
prometheus_port=${PROMETHEUS_PORT:-${file_prometheus_port:-9090}}
grafana_port=${GRAFANA_PORT:-${file_grafana_port:-3000}}

for port in "${app_port}" "${prometheus_port}" "${grafana_port}"; do
  printf '%s\n' "${port}" | grep -Eq '^[1-9][0-9]{0,4}$' \
    || die "configured HTTP ports must be positive integers"
done

check_endpoint application-health "http://127.0.0.1:${app_port}/actuator/health"
check_endpoint openapi "http://127.0.0.1:${app_port}/v3/api-docs"
check_endpoint prometheus-health "http://127.0.0.1:${prometheus_port}/-/healthy"
check_endpoint grafana-health "http://127.0.0.1:${grafana_port}/api/health"

target_status=$(curl --silent --show-error \
  "http://127.0.0.1:${prometheus_port}/api/v1/targets" \
  | grep -o '"health":"up"' \
  | head -n 1 || true)
[ "${target_status}" = '"health":"up"' ] \
  || die "Prometheus does not report an UP target"
printf 'prometheus-target: UP\n'
