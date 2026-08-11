#!/bin/sh

set -eu

SCRIPT_DIR=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
DEPLOY_ROOT=$(CDPATH='' cd -- "${SCRIPT_DIR}/../.." && pwd)
COMPOSE_FILE=${FINGUARD_COMPOSE_FILE:-${DEPLOY_ROOT}/compose.linux.yml}
ENV_FILE=${FINGUARD_ENV_FILE:-${DEPLOY_ROOT}/.env.linux}
STATE_DIR=${FINGUARD_STATE_DIR:-${DEPLOY_ROOT}/.deploy-state}

env_file_value() {
  key=$1
  [ -f "${ENV_FILE}" ] || return 0
  awk -v key="${key}" '
    index($0, key "=") == 1 {
      print substr($0, length(key) + 2)
      exit
    }
  ' "${ENV_FILE}"
}

file_project_name=$(env_file_value FINGUARD_PROJECT_NAME)
PROJECT_NAME=${FINGUARD_PROJECT_NAME:-${file_project_name:-finguard}}

die() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || die "required command is unavailable: $1"
}

require_file() {
  [ -f "$1" ] || die "required file is unavailable: $1"
}

compose() {
  docker compose \
    --project-name "${PROJECT_NAME}" \
    --env-file "${ENV_FILE}" \
    --file "${COMPOSE_FILE}" \
    "$@"
}

validate_environment_file() {
  require_file "${ENV_FILE}"

  if command -v stat >/dev/null 2>&1; then
    mode=$(stat -c '%a' "${ENV_FILE}" 2>/dev/null || true)
    case "${mode}" in
      600|400) ;;
      *) die "${ENV_FILE} must have mode 600 or 400 (actual: ${mode:-unknown})" ;;
    esac
  fi

  printf '%s\n' "${PROJECT_NAME}" | grep -Eq '^[a-z0-9][a-z0-9_-]*$' \
    || die "FINGUARD_PROJECT_NAME contains unsupported characters"
}

resolved_app_image() {
  images=$(compose config --images | grep '^ghcr\.io/jkwang1129/finguard-core:' || true)
  count=$(printf '%s\n' "${images}" | sed '/^$/d' | wc -l | tr -d ' ')
  [ "${count}" = "1" ] || die "exactly one FinGuard GHCR image must be configured"
  printf '%s\n' "${images}"
}

validate_app_image() {
  image=$1
  printf '%s\n' "${image}" | grep -Eq '^ghcr\.io/jkwang1129/finguard-core:[0-9a-f]{40}$' \
    || die "FINGUARD_APP_IMAGE must use the full 40-character Git SHA tag"
  [ "${image#*:}" != "0000000000000000000000000000000000000000" ] \
    || die "the example zero SHA is not deployable"
}

prepare_state_dir() {
  umask 077
  mkdir -p "${STATE_DIR}"
  chmod 700 "${STATE_DIR}"
}

record_successful_image() {
  image=$1
  prepare_state_dir
  current_file=${STATE_DIR}/current-image
  previous_file=${STATE_DIR}/previous-image

  if [ -f "${current_file}" ]; then
    current=$(sed -n '1p' "${current_file}")
    if [ -n "${current}" ] && [ "${current}" != "${image}" ]; then
      printf '%s\n' "${current}" > "${previous_file}"
    fi
  fi

  printf '%s\n' "${image}" > "${current_file}"
}
