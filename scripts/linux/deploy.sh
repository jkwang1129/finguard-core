#!/bin/sh

set -eu

SCRIPT_DIR=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
# shellcheck source=common.sh
. "${SCRIPT_DIR}/common.sh"

require_command docker
validate_environment_file
docker info >/dev/null 2>&1 || die "Docker Engine is not reachable by the deployment user"
docker compose version >/dev/null 2>&1 || die "Docker Compose Plugin is unavailable"

compose config --quiet
app_image=$(resolved_app_image)
validate_app_image "${app_image}"

temporary_docker_config=
cleanup() {
  if [ -n "${temporary_docker_config}" ] && [ -d "${temporary_docker_config}" ]; then
    rm -r -- "${temporary_docker_config}"
  fi
}
trap cleanup EXIT HUP INT TERM

if [ -n "${GHCR_TOKEN:-}" ]; then
  [ -n "${GHCR_USERNAME:-}" ] || die "GHCR_USERNAME is required when GHCR_TOKEN is provided"
  temporary_docker_config=$(mktemp -d)
  chmod 700 "${temporary_docker_config}"
  export DOCKER_CONFIG="${temporary_docker_config}"
  printf '%s' "${GHCR_TOKEN}" \
    | docker login ghcr.io --username "${GHCR_USERNAME}" --password-stdin >/dev/null
fi

printf 'Pulling immutable application image and pinned dependencies...\n'
compose pull

file_wait_timeout=$(env_file_value FINGUARD_DEPLOY_WAIT_TIMEOUT)
wait_timeout=${FINGUARD_DEPLOY_WAIT_TIMEOUT:-${file_wait_timeout:-180}}
printf '%s\n' "${wait_timeout}" | grep -Eq '^[1-9][0-9]*$' \
  || die "FINGUARD_DEPLOY_WAIT_TIMEOUT must be a positive integer"
printf 'Starting FinGuard services and waiting for health checks...\n'
compose up --detach --no-build --wait --wait-timeout "${wait_timeout}"

compose ps
record_successful_image "${app_image}"
printf 'Deployment healthy: %s\n' "${app_image}"
