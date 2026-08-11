#!/bin/sh

set -eu

SCRIPT_DIR=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
# shellcheck source=common.sh
. "${SCRIPT_DIR}/common.sh"

require_command docker
validate_environment_file

printf '%s\n' '== Linux host =='
uname -srm
if [ -r /etc/os-release ]; then
  awk -F= '/^(NAME|VERSION|ID|VERSION_ID)=/ { print }' /etc/os-release
fi
printf 'architecture=%s\n' "$(uname -m)"
printf 'cpu_count=%s\n' "$(getconf _NPROCESSORS_ONLN 2>/dev/null || printf unknown)"

printf '%s\n' '== Memory and filesystem =='
if command -v free >/dev/null 2>&1; then
  free -h
fi
df -h "${DEPLOY_ROOT}"

printf '%s\n' '== Time =='
date --iso-8601=seconds 2>/dev/null || date
if command -v timedatectl >/dev/null 2>&1; then
  timedatectl show \
    --property=Timezone \
    --property=NTPSynchronized \
    --property=LocalRTC 2>/dev/null || true
fi

printf '%s\n' '== Docker =='
docker version --format 'client={{.Client.Version}} server={{.Server.Version}} os={{.Server.Os}} arch={{.Server.Arch}}'
docker compose version
docker info >/dev/null 2>&1 || die "Docker Engine is not reachable by the deployment user"

printf '%s\n' '== Existing containers =='
docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}'

printf '%s\n' '== Listening TCP ports =='
if command -v ss >/dev/null 2>&1; then
  ss -lnt
else
  printf '%s\n' 'ss is unavailable; verify host listening ports separately.'
fi

printf '%s\n' '== Firewall =='
if command -v ufw >/dev/null 2>&1; then
  ufw status 2>/dev/null || printf '%s\n' 'ufw status requires elevated permission.'
else
  printf '%s\n' 'ufw is unavailable; verify the host and cloud firewall separately.'
fi

printf '%s\n' '== FinGuard configuration =='
compose config --quiet
app_image=$(resolved_app_image)
validate_app_image "${app_image}"
printf 'project=%s\n' "${PROJECT_NAME}"
printf 'app_image=%s\n' "${app_image}"
printf '%s\n' 'Preflight checks completed without rendering secret values.'
