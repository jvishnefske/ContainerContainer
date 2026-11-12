#!/usr/bin/env bash
# Builds the overlay image: the stock Artifactory OSS image with the fork's artifactory-*.jar files swapped into
# its exploded webapp WEB-INF/lib, plus any runtime dependency the fork's WAR has and the stock image lacks.
#
# Prerequisite: a full reactor build of this checkout (`mvn -DskipTests install`), so every module's
# target/artifactory-<module>-<version>.jar and web/war/target/artifactory/WEB-INF/lib exist.
#
# Jars to replace are the stock jars named artifactory-*-<source version>.jar; each must have exactly one
# counterpart built in this checkout (taken from the module's own target/, not the shared Maven repository).
# Jars to add are those in the built WAR's WEB-INF/lib that the stock image does not have. Fails loudly when a
# stock jar it expects to replace has no built counterpart, or a built module jar is missing from the stock image.
#
# Usage: docker/build-overlay.sh [image-tag]
# Environment: BASE_IMAGE (default releases-docker.jfrog.io/jfrog/artifactory-oss:7.161.26)
set -euo pipefail

TAG="${1:-artifactory-oss-fork:7.161.26-dev}"
BASE_IMAGE="${BASE_IMAGE:-releases-docker.jfrog.io/jfrog/artifactory-oss:7.161.26}"
WEBAPP_LIB=/opt/jfrog/artifactory/app/artifactory/tomcat/webapps/artifactory/WEB-INF/lib

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WAR_LIB="$ROOT/web/war/target/artifactory/WEB-INF/lib"

die() { echo "build-overlay: FATAL: $*" >&2; exit 1; }
log() { echo "build-overlay: $*" >&2; }

[ -d "$WAR_LIB" ] || die "no built WAR at $WAR_LIB; run the Maven reactor build first"

# Source version: the version of the root pom (the upstream sources call 7.161.26 "7.161.15" internally).
SRC_VER="$(sed -n 's:^    <version>\(.*\)</version>$:\1:p' "$ROOT/pom.xml" | head -n 1)"
[ -n "$SRC_VER" ] || die "cannot read the project version from $ROOT/pom.xml"

docker image inspect "$BASE_IMAGE" >/dev/null 2>&1 || docker pull "$BASE_IMAGE" >&2
BASE_DIGEST="$(docker image inspect "$BASE_IMAGE" --format '{{index .RepoDigests 0}}' 2>/dev/null || true)"
log "base image $BASE_IMAGE ${BASE_DIGEST:+($BASE_DIGEST)}, source version $SRC_VER"

CTX="$(mktemp -d "${TMPDIR:-/tmp}/rt-overlay.XXXXXX")"
trap 'rm -rf "$CTX"' EXIT
mkdir -p "$CTX/overlay/lib"
cp "$ROOT/docker/Dockerfile" "$CTX/Dockerfile"

docker run --rm --network none --entrypoint ls "$BASE_IMAGE" "$WEBAPP_LIB" | LC_ALL=C sort > "$CTX/overlay/stock"
[ -s "$CTX/overlay/stock" ] || die "could not list $WEBAPP_LIB in $BASE_IMAGE"
grep -qx "artifactory-core-$SRC_VER.jar" "$CTX/overlay/stock" \
    || die "stock image has no artifactory-core-$SRC_VER.jar; is BASE_IMAGE the matching release?"

# Jars to replace: every stock artifactory-*-<SRC_VER>.jar, each built exactly once in this checkout.
: > "$CTX/overlay/replace"
while read -r jar; do
    mapfile -t built < <(find "$ROOT" -path "$ROOT/web/war" -prune -o -path '*/node_modules' -prune -o \
        -type f -path "*/target/$jar" -print)
    [ "${#built[@]}" -eq 1 ] || die "stock jar $jar has ${#built[@]} built counterparts (${built[*]:-none})"
    cp "${built[0]}" "$CTX/overlay/lib/$jar"
    echo "$jar" >> "$CTX/overlay/replace"
    log "replace $jar <- ${built[0]#"$ROOT/"}"
done < <(grep -x "artifactory-.*-$SRC_VER\.jar" "$CTX/overlay/stock")

# Every module jar the WAR packages must be one the stock image has (so it is replaced, never silently added).
while read -r jar; do
    grep -qx "$jar" "$CTX/overlay/replace" || die "WAR jar $jar is not in the stock image; refusing to add it"
done < <(ls "$WAR_LIB" | grep -x "artifactory-.*-$SRC_VER\.jar")

# Jars to add: runtime dependencies of the built WAR that the stock image lacks.
: > "$CTX/overlay/add"
while read -r jar; do
    grep -qx "$jar" "$CTX/overlay/stock" && continue
    cp "$WAR_LIB/$jar" "$CTX/overlay/lib/$jar"
    echo "$jar" >> "$CTX/overlay/add"
    log "add     $jar"
done < <(ls "$WAR_LIB" | LC_ALL=C sort)

# Informational: stock jars the fork's WAR would not package (the overlay keeps them).
while read -r jar; do
    [ -e "$WAR_LIB/$jar" ] || log "note: stock jar $jar is not in the built WAR (kept as is)"
done < "$CTX/overlay/stock"

[ -s "$CTX/overlay/replace" ] || die "nothing to replace"
LC_ALL=C sort -u "$CTX/overlay/stock" "$CTX/overlay/add" > "$CTX/overlay/expected"

log "building $TAG ($(wc -l < "$CTX/overlay/replace") replaced, $(wc -l < "$CTX/overlay/add") added)"
docker build --pull=false --build-arg "BASE_IMAGE=$BASE_IMAGE" \
    --label "org.opencontainers.image.base.name=$BASE_IMAGE" \
    ${BASE_DIGEST:+--label "org.opencontainers.image.base.digest=${BASE_DIGEST#*@}"} \
    -t "$TAG" "$CTX" >&2
log "built $TAG"
