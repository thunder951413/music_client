#!/usr/bin/env bash
set -euo pipefail
service_dir="$HOME/services/tempus-hub"
test -f "$service_dir/navidrome.before-gateway.toml"
systemctl --user disable --now tempus-gateway.service
install -m 600 "$service_dir/navidrome.before-gateway.toml" "$HOME/services/qq-music/navidrome.toml"
printf 'NAVIDROME_URL=http://127.0.0.1:4533\n' > "$service_dir/hub.env"
systemctl --user restart qq-music.service tempus-hub.service
