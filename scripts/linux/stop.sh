#!/bin/sh

set -eu

SCRIPT_DIR=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
# shellcheck source=common.sh
. "${SCRIPT_DIR}/common.sh"

require_command docker
validate_environment_file
compose config --quiet

printf 'Stopping FinGuard containers without deleting containers, networks, or volumes...\n'
compose stop --timeout 30
compose ps --all
printf 'FinGuard stopped; named volumes were preserved.\n'
