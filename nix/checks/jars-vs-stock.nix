# checks.jars-vs-stock: the jar set and manifest agree with the committed stock listing (nix/stock-lib.txt), with
# the same rules docker/build-overlay.sh and docker/Dockerfile enforce against a live image:
#   - replace.txt is exactly the stock artifactory-*-7.161.15.jar set, add.txt is disjoint from stock;
#   - lib/ is exactly replace + add, no jar executable (the store makes them 0444; installers use 0644), and each sha256 matches manifest.json;
#   - expected.txt (the listing WEB-INF/lib must have after the overlay) is stock + add.
{
  runCommand,
  jq,
  jars,
}:
runCommand "check-jars-vs-stock" { nativeBuildInputs = [ jq ]; } ''
  set -euo pipefail
  cd ${jars}
  export LC_ALL=C
  grep -x 'artifactory-.*-7\.161\.15\.jar' stock.txt > "$TMPDIR/stock-artifactory"
  diff "$TMPDIR/stock-artifactory" <(sort replace.txt)
  [ "$(wc -l < replace.txt)" = 18 ] || { echo "expected 18 jars to replace" >&2; exit 1; }
  [ -z "$(comm -12 <(sort add.txt) stock.txt)" ] || { echo "a jar to add is already stock" >&2; exit 1; }
  diff <(ls lib | sort) <(sort -u replace.txt add.txt)
  diff expected.txt <(sort -u stock.txt add.txt)
  bad="$(find lib -type f -perm /111)"; [ -z "$bad" ] || { echo "executable jar: $bad" >&2; exit 1; }
  jq -r '.jars[] | "\(.sha256)  lib/\(.name)"' manifest.json | sha256sum -c --quiet
  jq -e '.sourceRev | length > 0' manifest.json
  { echo "replace: $(wc -l < replace.txt), add: $(cat add.txt | tr '\n' ' ')"; cat manifest.json; } > $out
''
