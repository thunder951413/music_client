#!/usr/bin/env bash
# Run from a transferred release's source directory on the Linux build host.
set -euo pipefail
release_dir="$(cd "$(dirname "$0")/.." && pwd)"
cache_dir="${HUB_CARGO_CACHE:-$HOME/.cache/tempus-hub-cargo}"
mkdir -p "$cache_dir"
if test -n "${HUB_CARGO:-}"; then
  cd "$release_dir"
  args=(build --release --locked -j 2)
  if test -n "${HUB_VENDOR_DIR:-}"; then
    args+=(--offline --config 'source.crates-io.replace-with="vendored-sources"'
      --config "source.vendored-sources.directory=\"$HUB_VENDOR_DIR\"")
  fi
  exec "$HUB_CARGO" "${args[@]}"
fi
docker run --rm --cpus=2 --memory=2g --user "$(id -u):$(id -g)" \
  -e CARGO_HOME=/cargo -v "$cache_dir:/cargo" -v "$release_dir:/work" -w /work \
  "${HUB_RUST_IMAGE:-rust:1-slim}" cargo build --release --locked
