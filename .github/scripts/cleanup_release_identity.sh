#!/usr/bin/env bash
set -euo pipefail
: "${RUNNER_TEMP:?RUNNER_TEMP is required}"
identity_dir="$RUNNER_TEMP/enwokoma-release-signing"
# Stop only the isolated agent we started, never the user's existing agent.
if [[ -f "$identity_dir/agent.pid" ]]; then
  SSH_AUTH_SOCK="$(cat "$identity_dir/agent.socket")" SSH_AGENT_PID="$(cat "$identity_dir/agent.pid")" \
    ssh-agent -k >/dev/null || true
fi
rm -rf "$identity_dir"
