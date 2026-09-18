#!/usr/bin/env bash
set -euo pipefail

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAVA_HOME="${JAVA_HOME:-$BASE_DIR/jdk17}"
JAVA_BIN="$JAVA_HOME/bin/java"
APP_JAR="$BASE_DIR/cmdb.jar"
PID_FILE="$BASE_DIR/cmdb.pid"
LOG_DIR="$BASE_DIR/logs"
APP_LOG="$LOG_DIR/cmdb-console.log"
ENV_FILE="$BASE_DIR/cmdb.env"

if [[ ! -x "$JAVA_BIN" ]]; then
  echo "Java 17 not found: $JAVA_BIN" >&2
  exit 1
fi
if [[ ! -f "$APP_JAR" ]]; then
  echo "Application jar not found: $APP_JAR" >&2
  exit 1
fi
if [[ ! -f "$ENV_FILE" ]]; then
  echo "Runtime configuration not found: $ENV_FILE" >&2
  exit 1
fi

if [[ -f "$PID_FILE" ]]; then
  old_pid="$(cat "$PID_FILE")"
  if kill -0 "$old_pid" 2>/dev/null; then
    echo "CMDB is already running, pid=$old_pid"
    exit 0
  fi
  rm -f "$PID_FILE"
fi

mkdir -p "$LOG_DIR"
set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

cd "$BASE_DIR"
nohup "$JAVA_BIN" ${JAVA_OPTS:-} -jar "$APP_JAR" >>"$APP_LOG" 2>&1 &
pid=$!
echo "$pid" > "$PID_FILE"
sleep 2

if ! kill -0 "$pid" 2>/dev/null; then
  echo "CMDB failed to start; check $APP_LOG" >&2
  rm -f "$PID_FILE"
  exit 1
fi

echo "CMDB started, pid=$pid, port=${SERVER_PORT:-9090}"
