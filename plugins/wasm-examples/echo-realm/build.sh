#!/usr/bin/env bash
# Builds the echo-realm example WASM plugin reproducibly in a pinned Rust image and runs its unit tests.
#
#   build.sh          build, test, and refresh the committed test fixture and its .sha256
#   build.sh --check  build and test in a temporary directory; fail unless the result matches the committed .sha256
#
# Reproducibility: the image is pinned by digest, dependencies by Cargo.lock (--locked), the source and Cargo home are
# remapped to fixed paths, debug info and symbols are stripped, and every build starts from an empty target directory.
set -euo pipefail

image='rust:1.91.1@sha256:867f1d1162913c401378a8504fb17fe2032c760dc316448766f150a130204aad'
crate_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$crate_dir/../../.." && pwd)"
fixture_dir="$repo_root/backend/core/src/test/resources/wasm"
fixture="$fixture_dir/echo-realm.wasm"
fixture_sha="$fixture.sha256"

case "${1:-}" in
  '') mode=build ;;
  --check) mode=check ;;
  *) echo "usage: $0 [--check]" >&2; exit 2 ;;
esac

out_dir="$(mktemp -d)"
trap 'rm -rf "$out_dir"' EXIT

# Paths inside the container. Both are remapped in the binary, so they never leak into it.
src=/work/echo-realm
cargo_home=/tmp/cargo-home
rustflags="--remap-path-prefix=$src=/echo-realm --remap-path-prefix=$cargo_home=/cargo -C debuginfo=0 -C strip=symbols"

docker run --rm \
  --user "$(id -u):$(id -g)" \
  --volume "$crate_dir:$src:ro" \
  --volume "$out_dir:/out" \
  --workdir "$src" \
  --env CARGO_HOME="$cargo_home" \
  --env CARGO_TARGET_DIR=/tmp/target \
  --env CARGO_INCREMENTAL=0 \
  --env SOURCE_DATE_EPOCH=0 \
  --env RUSTFLAGS="$rustflags" \
  "$image" \
  bash -euo pipefail -c '
    rustup target add wasm32-wasip1
    cargo test --locked
    cargo build --locked --release --target wasm32-wasip1
    cp /tmp/target/wasm32-wasip1/release/echo-realm.wasm /out/echo-realm.wasm
  '

built_sha="$(sha256sum "$out_dir/echo-realm.wasm" | cut -d' ' -f1)"

if [[ "$mode" == build ]]; then
  mkdir -p "$fixture_dir"
  install -m 0644 "$out_dir/echo-realm.wasm" "$fixture"
  printf '%s  echo-realm.wasm\n' "$built_sha" > "$fixture_sha"
  echo "echo-realm.wasm sha256 $built_sha -> $fixture"
else
  committed_sha="$(cut -d' ' -f1 "$fixture_sha")"
  if [[ "$built_sha" != "$committed_sha" ]]; then
    echo "echo-realm.wasm is not reproducible: built $built_sha, committed $committed_sha" >&2
    exit 1
  fi
  echo "echo-realm.wasm matches the committed sha256 $built_sha"
fi
