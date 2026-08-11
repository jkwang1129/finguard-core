#!/bin/sh

set -eu

SCRIPT_DIR=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
# shellcheck source=common.sh
. "${SCRIPT_DIR}/common.sh"

require_command docker
validate_environment_file
compose config --quiet

service=${1:-app}
case "${service}" in
  app|mysql|rabbitmq|redis|prometheus|grafana) ;;
  *) die "service must be one of: app, mysql, rabbitmq, redis, prometheus, grafana" ;;
esac

tail_lines=${2:-100}
printf '%s\n' "${tail_lines}" | grep -Eq '^[1-9][0-9]{0,2}$' \
  || die "tail line count must be an integer from 1 to 999"

printf 'Showing the last %s lines for %s without following the stream.\n' \
  "${tail_lines}" "${service}"
compose logs --no-color --tail "${tail_lines}" "${service}"
