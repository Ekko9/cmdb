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

is_cmdb_process() {
  local process_id="$1"
  [[ "$process_id" =~ ^[0-9]+$ ]] || return 1
  [[ -r "/proc/$process_id/cmdline" ]] || return 1
  tr '\0' ' ' < "/proc/$process_id/cmdline" | grep -Fq -- "$APP_JAR"
}

port_is_open() {
  local target_port="$1"
  (exec 3<>"/dev/tcp/127.0.0.1/$target_port") 2>/dev/null
}

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
  if kill -0 "$old_pid" 2>/dev/null && is_cmdb_process "$old_pid"; then
    echo "CMDB is already running, pid=$old_pid"
    exit 0
  fi
  if kill -0 "$old_pid" 2>/dev/null; then
    echo "Stale pid file points to another process, removing: pid=$old_pid" >&2
  fi
  rm -f "$PID_FILE"
fi

mkdir -p "$LOG_DIR"
set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

port="${SERVER_PORT:-9090}"
startup_timeout="${CMDB_STARTUP_TIMEOUT:-90}"
if [[ ! "$port" =~ ^[0-9]+$ ]] || (( port < 1 || port > 65535 )); then
  echo "SERVER_PORT must be an integer between 1 and 65535" >&2
  exit 1
fi
if [[ ! "$startup_timeout" =~ ^[0-9]+$ ]] || (( startup_timeout < 1 )); then
  echo "CMDB_STARTUP_TIMEOUT must be a positive integer" >&2
  exit 1
fi
if ! command -v curl >/dev/null 2>&1; then
  echo "curl is required for the readiness check" >&2
  exit 1
fi
if port_is_open "$port"; then
  echo "Port $port is already in use; refusing to start an unverified second instance" >&2
  exit 1
fi

cd "$BASE_DIR"
nohup "$JAVA_BIN" ${JAVA_OPTS:-} -jar "$APP_JAR" >>"$APP_LOG" 2>&1 &
pid=$!
echo "$pid" > "$PID_FILE"

for ((second = 1; second <= startup_timeout; second++)); do
  if ! kill -0 "$pid" 2>/dev/null; then
    echo "CMDB failed to start; check $APP_LOG" >&2
    rm -f "$PID_FILE"
    exit 1
  fi
  if curl --fail --silent --show-error --max-time 2 \
      "http://127.0.0.1:${port}/actuator/health/readiness" >/dev/null; then
    sleep 1
    if ! kill -0 "$pid" 2>/dev/null || ! is_cmdb_process "$pid"; then
      echo "Readiness responded, but the new CMDB process is not running; check $APP_LOG" >&2
      rm -f "$PID_FILE"
      exit 1
    fi
    echo "CMDB started and ready, pid=$pid, port=$port"
    exit 0
  fi
  sleep 1
done

echo "CMDB readiness check timed out after ${startup_timeout}s; check $APP_LOG" >&2
kill -TERM "$pid" 2>/dev/null || true
sleep 2
if kill -0 "$pid" 2>/dev/null; then kill -KILL "$pid" 2>/dev/null || true; fi
if kill -0 "$pid" 2>/dev/null; then
  echo "Failed to clean up the timed-out CMDB process, pid=$pid; pid file retained" >&2
  exit 1
fi
rm -f "$PID_FILE"
exit 1
