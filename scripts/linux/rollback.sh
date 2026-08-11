#!/bin/sh

set -eu

SCRIPT_DIR=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
# shellcheck source=common.sh
. "${SCRIPT_DIR}/common.sh"

validate_environment_file
prepare_state_dir

target_image=${1:-}
if [ -z "${target_image}" ]; then
  previous_file=${STATE_DIR}/previous-image
  require_file "${previous_file}"
  target_image=$(sed -n '1p' "${previous_file}")
fi
validate_app_image "${target_image}"

current_image=$(resolved_app_image)
[ "${target_image}" != "${current_image}" ] \
  || die "rollback target is already configured"

umask 077
temp_env=$(mktemp "${ENV_FILE}.tmp.XXXXXX")
cleanup() {
  [ ! -e "${temp_env}" ] || rm -f -- "${temp_env}"
}
trap cleanup EXIT HUP INT TERM

awk -v image="${target_image}" '
  BEGIN { replaced = 0 }
  /^FINGUARD_APP_IMAGE=/ {
    print "FINGUARD_APP_IMAGE=" image
    replaced = 1
    next
  }
  { print }
  END {
    if (!replaced) {
      print "FINGUARD_APP_IMAGE=" image
    }
  }
' "${ENV_FILE}" > "${temp_env}"
chmod 600 "${temp_env}"
mv -f -- "${temp_env}" "${ENV_FILE}"
trap - EXIT HUP INT TERM

printf 'Rolling back application image to %s\n' "${target_image}"
exec "${SCRIPT_DIR}/deploy.sh"
