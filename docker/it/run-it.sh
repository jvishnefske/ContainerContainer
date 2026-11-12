#!/usr/bin/env bash
# Live integration test of the fork on the stock Artifactory OSS 7.161.26 image (requirements B2, R2-R7).
#
#  1. Builds the overlay image (docker/build-overlay.sh) tagged artifactory-oss-fork:7.161.26-dev.
#  2. Phase "stock": starts the unmodified stock image on a fresh volume, creates a Generic repository and stores
#     a file in it, then stops. This is the "existing data" of B2.
#  3. Phase "fork": seeds plugins/examples/simple-index.groovy into the volume and starts the overlay image on the
#     same volume, then runs the assertions below against 127.0.0.1 only.
#
# Containers are named rt-fork-it*, the volume rt-fork-it-var; leftovers with exactly those names are removed
# first, and everything is removed again on exit. Both images use their default embedded database (Derby); since
# 7.161 that needs JF_SHARED_DATABASE_ALLOWNONPOSTGRESQL=true, the stock opt-in for non-PostgreSQL databases.
#
# Repository creation: in OSS the public repository REST API (PUT /api/repositories/{key}) is a Pro feature
# (RestAddonDefaultImpl throws MissingRestAddonException), so repositories are created with the OSS YAML
# configuration API, PATCH /api/system/configuration, whose localRepositories entries accept
# `pluginPackageType` for Generic repositories. The PUT attempt is reported as INFO for the record.
#
# Usage: docker/it/run-it.sh            (env: SKIP_BUILD=1 reuses an existing image; LOG_DIR keeps container logs)
# Exit status: 0 when every assertion passes, 1 otherwise.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
IMAGE=artifactory-oss-fork:7.161.26-dev
STOCK_IMAGE=releases-docker.jfrog.io/jfrog/artifactory-oss:7.161.26
NAME=rt-fork-it
STOCK_NAME=rt-fork-it-stock
VOLUME=rt-fork-it-var
VAR=/var/opt/jfrog/artifactory
PLUGINS_DIR=$VAR/etc/artifactory/plugins
PLUGIN_SRC="$ROOT/plugins/examples/simple-index.groovy"
ADMIN=admin:password
STARTUP_TIMEOUT=600
INDEX_TIMEOUT=90
LOG_DIR="${LOG_DIR:-$(mktemp -d "${TMPDIR:-/tmp}/rt-fork-it-logs.XXXXXX")}"
WORK="$(mktemp -d "${TMPDIR:-/tmp}/rt-fork-it-work.XXXXXX")"

PASSED=0
FAILED=0
RESULTS=()

say() { echo "run-it: $*" >&2; }
record() { # record <PASS|FAIL> <name> <detail>
    RESULTS+=("$1 $2${3:+ - $3}")
    if [ "$1" = PASS ]; then PASSED=$((PASSED + 1)); else FAILED=$((FAILED + 1)); fi
    echo "$1 $2${3:+ - $3}"
}
check() { # check <name> <condition-command...>; the detail is taken from $DETAIL
    local name="$1"; shift
    if "$@"; then record PASS "$name" "${DETAIL:-}"; else record FAIL "$name" "${DETAIL:-}"; fi
    DETAIL=""
}

remove_all() {
    docker rm -f "$NAME" "$STOCK_NAME" >/dev/null 2>&1 || true
    docker volume rm "$VOLUME" >/dev/null 2>&1 || true
}
cleanup() {
    local rc=$?
    for c in "$STOCK_NAME" "$NAME"; do
        if docker container inspect "$c" >/dev/null 2>&1; then
            docker logs "$c" > "$LOG_DIR/$c.log" 2>&1 || true
        fi
    done
    remove_all
    rm -rf "$WORK"
    say "container logs kept in $LOG_DIR"
    exit "$rc"
}
trap cleanup EXIT
trap 'exit 130' INT TERM

# --- HTTP helpers (127.0.0.1 only). Each sets CODE and writes the body to $WORK/body. -----------------------------
http() { # http <curl args...>
    CODE="$(curl -s -m 60 -o "$WORK/body" -w '%{http_code}' "$@")" || CODE=000
}
body() { cat "$WORK/body"; }
body_has() { grep -q -- "$1" "$WORK/body"; }

wait_for_ping() { # wait_for_ping <container>; bounded by STARTUP_TIMEOUT
    local container="$1" waited=0
    while [ "$waited" -lt "$STARTUP_TIMEOUT" ]; do
        if [ "$(docker inspect -f '{{.State.Running}}' "$container" 2>/dev/null)" != true ]; then
            say "$container is not running"; return 1
        fi
        http "$BASE/api/system/ping"
        if [ "$CODE" = 200 ] && [ "$(body)" = OK ]; then say "$container answered ping after ${waited}s"; return 0; fi
        sleep 5; waited=$((waited + 5))
    done
    say "$container did not answer ping within ${STARTUP_TIMEOUT}s"; return 1
}

start_container() { # start_container <name> <image>
    docker run -d --name "$1" \
        -e JF_SHARED_DATABASE_ALLOWNONPOSTGRESQL=true \
        -p "127.0.0.1:$PORT:8082" \
        -v "$VOLUME:$VAR" \
        "$2" >/dev/null
}

yaml_create_local() { # yaml_create_local <key> [pluginPackageType]
    {
        printf 'localRepositories:\n  %s:\n    type: generic\n' "$1"
        [ -z "${2:-}" ] || printf '    pluginPackageType: %s\n' "$2"
    } > "$WORK/patch.yaml"
    http -u "$ADMIN" -X PATCH -H 'Content-Type: application/yaml' --data-binary "@$WORK/patch.yaml" \
        "$BASE/api/system/configuration"
}

repo_listed() { # repo_listed <key>; uses the OSS repository listing
    http -u "$ADMIN" "$BASE/api/repositories"
    [ "$CODE" = 200 ] && grep -q "\"key\" *: *\"$1\"" "$WORK/body"
}

# --- Setup --------------------------------------------------------------------------------------------------------
[ -f "$PLUGIN_SRC" ] || { say "missing $PLUGIN_SRC"; exit 1; }

leftovers="$(docker ps -a --filter "name=^rt-fork-it" --format '{{.Names}}')"
[ -z "$leftovers" ] || say "removing leftovers: $leftovers"
remove_all

# Pick the first port from 18082 with no local listener (reads this host's socket table; sends no traffic).
PORT=18082
while ss -ltnH "sport = :$PORT" | grep -q .; do
    PORT=$((PORT + 1))
    [ "$PORT" -le 18099 ] || { say "no free port in 18082-18099 on this host"; exit 1; }
done
BASE="http://127.0.0.1:$PORT/artifactory"
say "using $BASE"

if [ "${SKIP_BUILD:-0}" != 1 ]; then
    "$ROOT/docker/build-overlay.sh" "$IMAGE" || { say "overlay build failed"; exit 1; }
fi
docker image inspect "$IMAGE" >/dev/null || { say "no image $IMAGE"; exit 1; }
STOCK_DIGEST="$(docker image inspect "$STOCK_IMAGE" --format '{{index .RepoDigests 0}}')"
FORK_ID="$(docker image inspect "$IMAGE" --format '{{.Id}}')"
say "stock image $STOCK_DIGEST; fork image $FORK_ID"

docker volume create "$VOLUME" >/dev/null

# --- Phase "stock": existing data written by the unmodified image ---------------------------------------------------
say "phase stock: starting $STOCK_IMAGE"
start_container "$STOCK_NAME" "$STOCK_IMAGE"
if wait_for_ping "$STOCK_NAME"; then
    head -c 4096 /dev/urandom > "$WORK/stock.bin"
    yaml_create_local stock-local
    stock_repo="$CODE"
    http -u "$ADMIN" -X PUT -T "$WORK/stock.bin" "$BASE/stock-local/data/stock.bin"
    DETAIL="create repo $stock_repo, deploy $CODE"
    check "B2.stock-data-written" test "$stock_repo" = 200 -a "$CODE" = 201
else
    DETAIL="stock image did not start"; check "B2.stock-data-written" false
fi
docker logs "$STOCK_NAME" > "$LOG_DIR/$STOCK_NAME.log" 2>&1 || true
docker stop -t 120 "$STOCK_NAME" >/dev/null 2>&1 || true
docker rm -f "$STOCK_NAME" >/dev/null 2>&1 || true

# --- Phase "fork": overlay image on the same volume, with the example plugin ---------------------------------------
docker run --rm -i --name rt-fork-it-seed --network none -v "$VOLUME:$VAR" --entrypoint sh "$IMAGE" \
    -c "mkdir -p '$PLUGINS_DIR' && cat > '$PLUGINS_DIR/simple-index.groovy'" < "$PLUGIN_SRC"
say "phase fork: starting $IMAGE"
start_container "$NAME" "$IMAGE"
if wait_for_ping "$NAME"; then
    DETAIL="ping OK on existing data"; check "B2.fork-starts-on-existing-data" true
else
    DETAIL="see $LOG_DIR/$NAME.log"; check "B2.fork-starts-on-existing-data" false
    docker logs "$NAME" > "$LOG_DIR/$NAME.log" 2>&1
    exit 1
fi

http -u "$ADMIN" "$BASE/stock-local/data/stock.bin"
cmp -s "$WORK/body" "$WORK/stock.bin"; same=$?
DETAIL="GET $CODE, content identical: $([ $same = 0 ] && echo yes || echo no)"
check "B2.stock-data-readable" test "$CODE" = 200 -a "$same" = 0

# Artifactory logs "Triggering <TYPE> conversion, from A to B" on every start; only A != B is a migration.
docker logs "$NAME" 2>&1 | sed -n 's/.*Triggering \([A-Z_]*\) conversion, from \([^ ]*\) to \([^ ]*\).*/\1 \2 \3/p' \
    > "$WORK/conversions"
migrations="$(awk '$2 != $3' "$WORK/conversions" | wc -l)"
DETAIL="$(wc -l < "$WORK/conversions") conversion checks ($(awk '{print $1": "$2"->"$3}' "$WORK/conversions" \
    | paste -sd, -)), $migrations between different versions"
check "B2.no-schema-migration" test "$migrations" = 0 -a -s "$WORK/conversions"

# --- G5/G4: plugin listing and method check ------------------------------------------------------------------------
http -u "$ADMIN" "$BASE/api/plugins"
DETAIL="GET /api/plugins $CODE"
check "G5.plugins-lists-simpleidxReindex" eval '[ "$CODE" = 200 ] && body_has "\"simpleidxReindex\""'

http -u "$ADMIN" "$BASE/api/plugins/execute/simpleidxReindex"
DETAIL="GET execute $CODE"
check "G4.execute-wrong-method-405" test "$CODE" = 405

# --- R2: plugin package type validation on repository create --------------------------------------------------------
http -u "$ADMIN" -X PUT -H 'Content-Type: application/json' \
    -d '{"rclass":"local","packageType":"generic","packageTypeConfig":{"pluginPackageType":"nosuch"}}' \
    "$BASE/api/repositories/bad-local"
say "INFO public PUT /api/repositories/bad-local -> $CODE: $(body | tr -d '\n' | cut -c1-120)"

yaml_create_local bad-local nosuch
DETAIL="PATCH $CODE: $(body | tr -d '\n' | cut -c1-160)"
check "R2.unknown-type-rejected-400-naming-it" eval '[ "$CODE" = 400 ] && body_has "nosuch"'
DETAIL="bad-local must not exist"
check "R2.unknown-type-not-stored" eval '! repo_listed bad-local'

yaml_create_local simple-local simpleidx
DETAIL="PATCH $CODE: $(body | tr -d '\n' | cut -c1-120)"
check "R2.registered-type-accepted" eval '[ "$CODE" = 200 ] && repo_listed simple-local'

# --- R4/R5/R3/R7: the example package type --------------------------------------------------------------------------
printf 'fake package\n' | gzip -c > "$WORK/a-1.0.tgz"
http -u "$ADMIN" -X PUT -T "$WORK/a-1.0.tgz" "$BASE/simple-local/a-1.0.tgz"
DETAIL="PUT $CODE"
check "R4.upload-tgz-201" test "$CODE" = 201

printf 'notes\n' > "$WORK/notes.txt"
http -u "$ADMIN" -X PUT -T "$WORK/notes.txt" "$BASE/simple-local/notes.txt"
DETAIL="PUT $CODE: $(body | tr -d '\n' | cut -c1-120)"
check "R4.upload-txt-rejected-400" test "$CODE" = 400

waited=0; indexed=1
while [ "$waited" -lt "$INDEX_TIMEOUT" ]; do
    http -u "$ADMIN" "$BASE/simple-local/index.json"
    if [ "$CODE" = 200 ] && body_has '"a-1.0.tgz"'; then indexed=0; break; fi
    sleep 2; waited=$((waited + 2))
done
DETAIL="after ${waited}s: GET $CODE $(body | tr -d '\n' | cut -c1-120)"
check "R5.R7.index-json-lists-a-1.0.tgz" test "$indexed" = 0
DETAIL="notes.txt must not be stored"
http -u "$ADMIN" "$BASE/simple-local/notes.txt"
check "R4.rejected-file-not-stored" test "$CODE" = 404

http "$BASE/simple-local/index.json"
DETAIL="anonymous GET $CODE"
check "R3.anonymous-index-denied" eval '[ "$CODE" = 401 ] || [ "$CODE" = 403 ]'

http -u "$ADMIN" -X POST "$BASE/api/plugins/execute/simpleidxReindex?params=repo=simple-local"
DETAIL="POST $CODE: $(body | tr -d '\n' | cut -c1-120)"
check "R7.reindex-execution" eval '[ "$CODE" = 200 ] && body_has "1 package"'

# --- R6: plugin removed, repository keeps working as plain Generic --------------------------------------------------
docker exec "$NAME" rm -f "$PLUGINS_DIR/simple-index.groovy"
http -u "$ADMIN" -X POST "$BASE/api/plugins/reload"
DETAIL="reload $CODE"
check "R6.reload-after-removal" test "$CODE" = 200
http -u "$ADMIN" "$BASE/api/plugins"
DETAIL="GET /api/plugins $CODE"
check "R6.execution-gone" eval '[ "$CODE" = 200 ] && ! body_has "simpleidxReindex"'

http -u "$ADMIN" "$BASE/simple-local/a-1.0.tgz"
cmp -s "$WORK/body" "$WORK/a-1.0.tgz"; same=$?
DETAIL="GET $CODE, content identical: $([ $same = 0 ] && echo yes || echo no)"
check "R6.stored-artifact-still-served" test "$CODE" = 200 -a "$same" = 0

http -u "$ADMIN" -X PUT -T "$WORK/notes.txt" "$BASE/simple-local/notes.txt"
DETAIL="PUT $CODE"
check "R6.plain-generic-upload-after-removal" test "$CODE" = 201

# --- Summary --------------------------------------------------------------------------------------------------------
echo
echo "== run-it summary ($(date -u +%Y-%m-%dT%H:%M:%SZ)) stock=$STOCK_DIGEST fork=$FORK_ID"
for line in "${RESULTS[@]}"; do echo "$line"; done
echo "== $PASSED passed, $FAILED failed"
[ "$FAILED" = 0 ]
