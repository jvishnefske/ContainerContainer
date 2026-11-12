#!/usr/bin/env bash
# Recomputes nix/mvn-hash.txt, the hash of the fixed-output derivation holding every Maven dependency the build
# downloads (packages.jarsBuild.fetchedMavenDeps). Run it whenever a pom.xml change adds, removes or bumps a
# dependency or plugin: the offline build then fails with a missing artifact until the hash is updated.
#
# It empties the hash, builds the dependency derivation once with network access, and writes the hash Nix reports.
# Usage: nix/update-mvn-hash.sh   (from anywhere in the checkout; needs nix with flakes)
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
NIX="${NIX:-nix}"

: > nix/mvn-hash.txt
log="$(mktemp)"
trap 'rm -f "$log"' EXIT
"$NIX" build .#jarsBuild.fetchedMavenDeps --no-link -L >"$log" 2>&1 || true
hash="$(sed -n 's/.*got: *\(sha256-[A-Za-z0-9+/=]*\).*/\1/p' "$log" | tail -n 1)"
if [ -z "$hash" ]; then
    tail -n 60 "$log" >&2
    echo "update-mvn-hash: no hash in the build output (see above); nix/mvn-hash.txt is left empty" >&2
    exit 1
fi
echo "$hash" > nix/mvn-hash.txt
echo "update-mvn-hash: nix/mvn-hash.txt = $hash"
