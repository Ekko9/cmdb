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
  echo "CMDB is not running"
  exit 0
fi

pid="$(cat "$PID_FILE")"
if [[ ! "$pid" =~ ^[0-9]+$ ]]; then
  echo "Invalid pid file: $PID_FILE" >&2
  exit 1
fi

if kill -0 "$pid" 2>/dev/null; then
  if ! is_cmdb_process "$pid"; then
    echo "Pid file points to a non-CMDB process, refusing to stop pid=$pid" >&2
    exit 1
  fi
  kill "$pid"
  for _ in {1..30}; do
    if ! kill -0 "$pid" 2>/dev/null; then
      break
    fi
    sleep 1
  done
  if kill -0 "$pid" 2>/dev/null; then
    echo "CMDB did not stop gracefully; sending KILL to pid=$pid" >&2
    kill -KILL "$pid" 2>/dev/null || true
    for _ in {1..5}; do
      if ! kill -0 "$pid" 2>/dev/null; then
        break
      fi
      sleep 1
    done
  fi
fi

if kill -0 "$pid" 2>/dev/null; then
  echo "CMDB process is still running, pid=$pid; pid file retained" >&2
  exit 1
fi

rm -f "$PID_FILE"
echo "CMDB stopped"
