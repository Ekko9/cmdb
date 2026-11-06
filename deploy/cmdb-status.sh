#!/usr/bin/env bash
set -euo pipefail

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PID_FILE="$BASE_DIR/cmdb.pid"
APP_JAR="$BASE_DIR/cmdb.jar"

is_cmdb_process() {
  local process_id="$1"
  [[ "$process_id" =~ ^[0-9]+$ ]] || return 1
  [[ -r "/proc/$process_id/cmdline" ]] || return 1
  tr '\0' ' ' < "/proc/$process_id/cmdline" | grep -Fq -- "$APP_JAR"
}

if [[ ! -f "$PID_FILE" ]]; then
  echo "CMDB is stopped"
  exit 3
fi

pid="$(cat "$PID_FILE")"
if [[ ! "$pid" =~ ^[0-9]+$ ]]; then
  echo "CMDB status is unknown (invalid pid file)" >&2
  exit 4
fi

if kill -0 "$pid" 2>/dev/null && is_cmdb_process "$pid"; then
  echo "CMDB is running, pid=$pid"
  exit 0
fi

if kill -0 "$pid" 2>/dev/null; then
  echo "CMDB is stopped (pid file points to another process, pid=$pid)" >&2
  exit 4
fi

echo "CMDB is stopped (stale pid file)"
exit 3
