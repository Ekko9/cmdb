#!/usr/bin/env bash
set -euo pipefail

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PID_FILE="$BASE_DIR/cmdb.pid"

if [[ ! -f "$PID_FILE" ]]; then
  echo "CMDB is stopped"
  exit 3
fi

pid="$(cat "$PID_FILE")"
if kill -0 "$pid" 2>/dev/null; then
  echo "CMDB is running, pid=$pid"
  exit 0
fi

echo "CMDB is stopped (stale pid file)"
exit 3
