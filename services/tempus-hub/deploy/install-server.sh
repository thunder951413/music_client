#!/usr/bin/env bash
# Run only after build-server.sh and tests pass. Keep the prior release for rollback.
set -euo pipefail
release_dir="$(cd "$(dirname "$0")/.." && pwd)"
service_dir="$HOME/services/tempus-hub"
test -x "$release_dir/target/release/tempus-hub"
mkdir -p "$service_dir/state" "$HOME/.config/systemd/user"
chmod 700 "$service_dir" "$service_dir/state"
if test -L "$service_dir/current"; then
  ln -sfn "$(readlink "$service_dir/current")" "$service_dir/previous"
fi
ln -sfn "$release_dir" "$service_dir/current"
install -m 600 "$release_dir/deploy/tempus-hub.service" "$HOME/.config/systemd/user/tempus-hub.service"
systemctl --user daemon-reload
systemctl --user enable --now tempus-hub.service
systemctl --user restart tempus-hub.service
