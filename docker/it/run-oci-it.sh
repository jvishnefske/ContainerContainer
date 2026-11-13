#!/usr/bin/env bash
# Live integration test of the OCI registry plugin (plugins/oci/oci-registry.groovy, requirements O1-O10) on the
# overlay image (docker/build-overlay.sh) of the stock Artifactory OSS 7.161.26 image.
#
#  1. Builds the overlay image tagged artifactory-oss-fork:7.161.26-oci-it (SKIP_BUILD=1 reuses it; IMAGE overrides).
#  2. Seeds the plugin into a fresh volume and starts the image, published on 127.0.0.1 only.
#  3. Creates two local Generic repositories of plugin type oci-registry (oci-local, and oci-other for the isolation
#     checks) through the YAML configuration API, and three users through the UI REST API (the public security API
#     is a Pro feature in OSS): ociadmin (administrator), deployer (read and deploy on oci-local, no delete) and
#     reader (read on oci-local only), with permission targets created the same way.
#  4. Asserts, printing one PASS/FAIL line each: docker push/pull (by tag and digest, same image id), podman
#     push/pull, podman multi-platform pushes as an OCI index and as a Docker manifest list, curl monolithic and
#     chunked uploads, manifests, tag pagination, deletes, permissions, path traversal and cross-repository mount
#     isolation, then runs the OCI distribution-spec v1.1.0 conformance suite and records its counts.
#
# Clients talk only to 127.0.0.1:<port> of the container this script starts. docker and podman credentials live in
# a temporary DOCKER_CONFIG / REGISTRY_AUTH_FILE, podman uses its own temporary storage, and docker trusts
# 127.0.0.0/8 as an insecure registry by default, so no daemon or system configuration is touched. The conformance
# image runs with --network host and is pointed at 127.0.0.1 only.
#
# Containers are named rt-oci-it*, the volume rt-oci-it-var; leftovers with those names are removed first, and
# everything (including the docker images tagged 127.0.0.1:<port>/...) is removed again on exit.
#
# Usage: docker/it/run-oci-it.sh   (env: SKIP_BUILD=1, IMAGE, LOG_DIR keeps container logs and the conformance report)
# Requires ADMIN_USER and ADMIN_PASSWORD in the environment: no default administrator credential is shipped.
# Exit status: 0 when every assertion passes, 1 otherwise.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
IMAGE="${IMAGE:-artifactory-oss-fork:7.161.26-oci-it}"
STOCK_IMAGE=releases-docker.jfrog.io/jfrog/artifactory-oss:7.161.26
CONFORMANCE_IMAGE=ghcr.io/opencontainers/distribution-spec/conformance:v1.1.0
CONFORMANCE_SRC=https://github.com/opencontainers/distribution-spec/archive/refs/tags/v1.1.0.tar.gz
NAME=rt-oci-it
VOLUME=rt-oci-it-var
VAR=/var/opt/jfrog/artifactory
PLUGINS_DIR=$VAR/etc/artifactory/plugins
PLUGIN_SRC="$ROOT/plugins/oci/oci-registry.groovy"
# Administrator credentials come from the environment; no default is shipped (design IA1).
: "${ADMIN_USER:?set ADMIN_USER (and ADMIN_PASSWORD) to the administrator credentials of the server under test}"
: "${ADMIN_PASSWORD:?set ADMIN_PASSWORD to the password of ADMIN_USER; there is no default password}"
ADMIN="$ADMIN_USER:$ADMIN_PASSWORD"
OCTET="Content-Type: application/octet-stream"
OCIADMIN_USER=ociadmin OCIADMIN_PASS='Admin-Passw0rd!'
DEPLOYER_USER=deployer DEPLOYER_PASS='Deployer-Passw0rd!'
READER_USER=reader READER_PASS='Reader-Passw0rd!'
STARTUP_TIMEOUT=600
LOG_DIR="${LOG_DIR:-$(mktemp -d "${TMPDIR:-/tmp}/rt-oci-it-logs.XXXXXX")}"
WORK="$(mktemp -d "${TMPDIR:-/tmp}/rt-oci-it-work.XXXXXX")"
# podman refuses a run root longer than 50 characters, so it gets a short directory of its own.
PODMAN_RUN="$(mktemp -d "${XDG_RUNTIME_DIR:-/tmp}/rt-oci-it.XXXXXX")"
mkdir -p "$LOG_DIR"

PASSED=0
FAILED=0
RESULTS=()

say() { echo "run-oci-it: $*" >&2; }
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

PODMAN=(podman --root "$WORK/podman-root" --runroot "$PODMAN_RUN" --storage-driver vfs)
export REGISTRY_AUTH_FILE="$WORK/podman-auth.json"
export CONTAINERS_REGISTRIES_CONF=/dev/null
export DOCKER_CONFIG="$WORK/docker-config"
mkdir -p "$DOCKER_CONFIG"

remove_all() {
    docker rm -f "$NAME" rt-oci-it-seed rt-oci-it-conformance >/dev/null 2>&1 || true
    docker volume rm "$VOLUME" >/dev/null 2>&1 || true
}
cleanup() {
    local rc=$?
    if docker container inspect "$NAME" >/dev/null 2>&1; then
        docker logs "$NAME" > "$LOG_DIR/$NAME.log" 2>&1 || true
        docker exec "$NAME" sh -c "cat $VAR/log/artifactory-service.log" > "$LOG_DIR/artifactory-service.log" 2>&1 || true
    fi
    cp "$WORK"/*.log "$LOG_DIR/" 2>/dev/null || true
    remove_all
    if [ -n "${PORT:-}" ]; then
        docker images --format '{{.Repository}}:{{.Tag}}' | grep "^127.0.0.1:$PORT/" | xargs -r docker rmi -f >/dev/null 2>&1
        docker images --format '{{.Repository}}@{{.Digest}}' | grep "^127.0.0.1:$PORT/" | xargs -r docker rmi -f >/dev/null 2>&1
    fi
    "${PODMAN[@]}" unshare rm -rf "$WORK/podman-root" "$PODMAN_RUN" >/dev/null 2>&1 || true
    rm -rf "$WORK" "$PODMAN_RUN" 2>/dev/null || true
    say "logs kept in $LOG_DIR"
    exit "$rc"
}
trap cleanup EXIT
trap 'exit 130' INT TERM

# --- HTTP helpers (127.0.0.1 only). Each sets CODE, writes the body to $WORK/body and the headers to $WORK/headers.
http() { # http <curl args...>
    CODE="$(curl -s -m 300 -D "$WORK/headers" -o "$WORK/body" -w '%{http_code}' "$@")" || CODE=000
}
body() { cat "$WORK/body"; }
body_has() { grep -q -- "$1" "$WORK/body"; }
hdr() { # hdr <name>: the first value of a response header, without CR
    grep -i "^$1:" "$WORK/headers" | head -n 1 | cut -d: -f2- | sed 's/^ *//' | tr -d '\r'
}
err_code() { jq -r '.errors[0].code // empty' "$WORK/body" 2>/dev/null; }
sha() { echo "sha256:$(sha256sum "$1" | cut -d' ' -f1)"; }
as_admin() { http -u "$OCIADMIN_USER:$OCIADMIN_PASS" "$@"; }

wait_for_ping() { # bounded by STARTUP_TIMEOUT
    local waited=0
    while [ "$waited" -lt "$STARTUP_TIMEOUT" ]; do
        if [ "$(docker inspect -f '{{.State.Running}}' "$NAME" 2>/dev/null)" != true ]; then
            say "$NAME is not running"; return 1
        fi
        http "$BASE/api/system/ping"
        if [ "$CODE" = 200 ] && [ "$(body)" = OK ]; then say "$NAME answered ping after ${waited}s"; return 0; fi
        sleep 5; waited=$((waited + 5))
    done
    say "$NAME did not answer ping within ${STARTUP_TIMEOUT}s"; return 1
}

ui_post() { # ui_post <path> <json>: the UI REST API, as the built-in admin
    http -u "$ADMIN" -X POST -H 'Content-Type: application/json' -H 'X-Requested-With: XMLHttpRequest' \
        -d "$2" "$BASE/ui/$1"
}

push_blob() { # push_blob <name> <file>: monolithic POST as ociadmin; sets CODE
    as_admin -X POST -H 'Content-Type: application/octet-stream' --data-binary "@$2" \
        "$REG/v2/oci-local/$1/blobs/uploads/?digest=$(sha "$2")"
}

# --- Setup --------------------------------------------------------------------------------------------------------
[ -f "$PLUGIN_SRC" ] || { say "missing $PLUGIN_SRC"; exit 1; }
for tool in docker podman curl jq python3 ss; do
    command -v "$tool" >/dev/null || { say "missing tool: $tool"; exit 1; }
done

leftovers="$(docker ps -a --filter "name=^rt-oci-it" --format '{{.Names}}')"
[ -z "$leftovers" ] || say "removing leftovers: $leftovers"
remove_all

# Pick the first port from 18182 with no local listener (reads this host's socket table; sends no traffic).
PORT=18182
while ss -ltnH "sport = :$PORT" | grep -q .; do
    PORT=$((PORT + 1))
    [ "$PORT" -le 18199 ] || { say "no free port in 18182-18199 on this host"; exit 1; }
done
REG="http://127.0.0.1:$PORT"
HOSTPORT="127.0.0.1:$PORT"
BASE="$REG/artifactory"
say "using $REG"

if [ "${SKIP_BUILD:-0}" != 1 ]; then
    "$ROOT/docker/build-overlay.sh" "$IMAGE" || { say "overlay build failed"; exit 1; }
fi
docker image inspect "$IMAGE" >/dev/null || { say "no image $IMAGE"; exit 1; }
STOCK_DIGEST="$(docker image inspect "$STOCK_IMAGE" --format '{{index .RepoDigests 0}}' 2>/dev/null || echo unknown)"
FORK_ID="$(docker image inspect "$IMAGE" --format '{{.Id}}')"
say "stock image $STOCK_DIGEST; fork image $FORK_ID"

docker volume create "$VOLUME" >/dev/null
docker run --rm -i --name rt-oci-it-seed --network none -v "$VOLUME:$VAR" --entrypoint sh "$IMAGE" \
    -c "mkdir -p '$PLUGINS_DIR' && cat > '$PLUGINS_DIR/oci-registry.groovy'" < "$PLUGIN_SRC"
docker run -d --name "$NAME" -e JF_SHARED_DATABASE_ALLOWNONPOSTGRESQL=true -p "127.0.0.1:$PORT:8082" \
    -v "$VOLUME:$VAR" "$IMAGE" >/dev/null
wait_for_ping || { DETAIL="see $LOG_DIR/$NAME.log"; check "setup.artifactory-starts" false; exit 1; }

http -u "$ADMIN" "$BASE/api/plugins"
DETAIL="GET /api/plugins $CODE"
check "setup.plugin-loaded" eval '[ "$CODE" = 200 ] && jq -e ".plugins[\"oci-registry\"].status == \"Loaded\"" "$WORK/body" >/dev/null'

printf 'localRepositories:\n' > "$WORK/repos.yaml"
for key in oci-local oci-other; do
    printf '  %s:\n    type: generic\n    pluginPackageType: oci-registry\n' "$key" >> "$WORK/repos.yaml"
done
http -u "$ADMIN" -X PATCH -H 'Content-Type: application/yaml' --data-binary "@$WORK/repos.yaml" \
    "$BASE/api/system/configuration"
DETAIL="PATCH $CODE: $(body | tr -d '\n' | cut -c1-120)"
check "setup.repositories-created" test "$CODE" = 200

created_users=0
for u in "$OCIADMIN_USER:$OCIADMIN_PASS:true" "$DEPLOYER_USER:$DEPLOYER_PASS:false" "$READER_USER:$READER_PASS:false"; do
    IFS=: read -r user pass isadmin <<< "$u"
    ui_post users "{\"name\":\"$user\",\"password\":\"$pass\",\"email\":\"$user@example.invalid\",\"admin\":$isadmin}"
    [ "$CODE" = 201 ] && created_users=$((created_users + 1))
done
permission() { # permission <name> <user> <actions json array> <repo>
    ui_post permissiontargets "{\"name\":\"$1\",\"repoPermission\":{\"repoKeys\":[\"$4\"],\"includePatterns\":[\"**\"],
        \"excludePatterns\":[],\"userPermissionActions\":[{\"principal\":\"$2\",\"actions\":$3}],
        \"groupPermissionActions\":[]}}"
    [ "$CODE" = 201 ]
}
permission oci-it-reader "$READER_USER" '["read"]' oci-local && permission oci-it-deployer "$DEPLOYER_USER" \
    '["read","upload"]' oci-local; perms=$?
DETAIL="$created_users users, permission targets $([ $perms = 0 ] && echo created || echo failed)"
check "setup.users-and-permissions" test "$created_users" = 3 -a "$perms" = 0

# --- GET /v2/ (Java ping) and anonymous challenge (O10) ---------------------------------------------------------
http "$REG/v2/"
DETAIL="anonymous GET /v2/ $CODE, WWW-Authenticate: $(hdr WWW-Authenticate)"
check "O10.anonymous-ping-challenged-401" eval '[ "$CODE" = 401 ] && hdr WWW-Authenticate | grep -q "^Basic"'
as_admin "$REG/v2/"
DETAIL="GET /v2/ $CODE, $(hdr Docker-Distribution-Api-Version)"
check "ping.authenticated-200-registry-2.0" eval '[ "$CODE" = 200 ] && [ "$(hdr Docker-Distribution-Api-Version)" = registry/2.0 ]'

# --- O1: docker push and pull of a single-platform image ---------------------------------------------------------
mkdir -p "$WORK/ctx"
echo "hello from run-oci-it $(date +%s%N)" > "$WORK/ctx/hello.txt"
printf 'FROM scratch\nCOPY hello.txt /hello.txt\nCMD ["/hello.txt"]\n' > "$WORK/ctx/Dockerfile"
DIMG="$HOSTPORT/oci-local/hello-docker"
echo "$OCIADMIN_PASS" | docker login "$HOSTPORT" -u "$OCIADMIN_USER" --password-stdin > "$WORK/docker-login.log" 2>&1
DETAIL="$(tail -n 1 "$WORK/docker-login.log")"
check "O1.docker-login" grep -q "Login Succeeded" "$WORK/docker-login.log"
DOCKER_BUILDKIT=0 docker build -q -t "$DIMG:1" "$WORK/ctx" > "$WORK/docker-build.log" 2>&1
DID="$(docker image inspect -f '{{.Id}}' "$DIMG:1" 2>/dev/null)"
docker push "$DIMG:1" > "$WORK/docker-push.log" 2>&1; rc=$?
DDIGEST="$(sed -n 's/.*digest: \(sha256:[a-f0-9]\{64\}\).*/\1/p' "$WORK/docker-push.log" | head -n 1)"
DETAIL="exit $rc, image $DID, manifest $DDIGEST"
check "O1.docker-push" test "$rc" = 0 -a -n "$DDIGEST"
docker rmi -f "$DIMG:1" "$DIMG@$DDIGEST" >/dev/null 2>&1
docker pull -q "$DIMG:1" >/dev/null 2>&1
DETAIL="pulled id $(docker image inspect -f '{{.Id}}' "$DIMG:1" 2>/dev/null)"
check "O1.docker-pull-by-tag-same-image-id" test -n "$DID" -a "$(docker image inspect -f '{{.Id}}' "$DIMG:1" 2>/dev/null)" = "$DID"
docker rmi -f "$DIMG:1" >/dev/null 2>&1
docker pull -q "$DIMG@$DDIGEST" >/dev/null 2>&1
DETAIL="pulled id $(docker image inspect -f '{{.Id}}' "$DIMG@$DDIGEST" 2>/dev/null)"
check "O1.docker-pull-by-digest-same-image-id" test -n "$DID" -a "$(docker image inspect -f '{{.Id}}' "$DIMG@$DDIGEST" 2>/dev/null)" = "$DID"

# --- O1 with podman (own storage, own auth file) ---------------------------------------------------------------
PIMG="$HOSTPORT/oci-local/hello-podman"
"${PODMAN[@]}" login --tls-verify=false -u "$OCIADMIN_USER" -p "$OCIADMIN_PASS" "$HOSTPORT" > "$WORK/podman.log" 2>&1
DETAIL="$(tail -n 1 "$WORK/podman.log")"
check "O1.podman-login" grep -q "Login Succeeded" "$WORK/podman.log"
"${PODMAN[@]}" build -q -t local/hello-podman:1 "$WORK/ctx" >> "$WORK/podman.log" 2>&1
PID="$("${PODMAN[@]}" image inspect -f '{{.Id}}' local/hello-podman:1 2>/dev/null)"
"${PODMAN[@]}" push --tls-verify=false --digestfile "$WORK/podman.digest" local/hello-podman:1 "docker://$PIMG:1" \
    >> "$WORK/podman.log" 2>&1; rc=$?
PDIGEST="$(cat "$WORK/podman.digest" 2>/dev/null)"
DETAIL="exit $rc, image $PID, manifest $PDIGEST"
check "O1.podman-push" test "$rc" = 0 -a -n "$PDIGEST"
"${PODMAN[@]}" rmi -f local/hello-podman:1 >/dev/null 2>&1
"${PODMAN[@]}" pull -q --tls-verify=false "$PIMG:1" >> "$WORK/podman.log" 2>&1
DETAIL="pulled id $("${PODMAN[@]}" image inspect -f '{{.Id}}' "$PIMG:1" 2>/dev/null)"
check "O1.podman-pull-by-tag-same-image-id" test -n "$PID" -a "$("${PODMAN[@]}" image inspect -f '{{.Id}}' "$PIMG:1" 2>/dev/null)" = "$PID"
"${PODMAN[@]}" rmi -f "$PIMG:1" >/dev/null 2>&1
"${PODMAN[@]}" pull -q --tls-verify=false "$PIMG@$PDIGEST" >> "$WORK/podman.log" 2>&1
DETAIL="pulled id $("${PODMAN[@]}" image inspect -f '{{.Id}}' "$PIMG@$PDIGEST" 2>/dev/null)"
check "O1.podman-pull-by-digest-same-image-id" test -n "$PID" -a "$("${PODMAN[@]}" image inspect -f '{{.Id}}' "$PIMG@$PDIGEST" 2>/dev/null)" = "$PID"
"${PODMAN[@]}" tag "$PIMG@$PDIGEST" local/hello-podman:2 >> "$WORK/podman.log" 2>&1
"${PODMAN[@]}" push --tls-verify=false --format v2s2 local/hello-podman:2 "docker://$PIMG:v2s2" >> "$WORK/podman.log" 2>&1
as_admin "$REG/v2/oci-local/hello-podman/manifests/v2s2"
DETAIL="GET $CODE $(hdr Content-Type)"
check "O5.podman-docker-schema2-push-served-as-schema2" eval '[ "$CODE" = 200 ] && [ "$(hdr Content-Type)" = application/vnd.docker.distribution.manifest.v2+json ]'

# --- O6: multi-platform image as an OCI index and as a Docker manifest list --------------------------------------
MIMG="$HOSTPORT/oci-local/multi"
for arch in amd64 arm64; do
    mkdir -p "$WORK/ctx-$arch"
    echo "$arch $(date +%s%N)" > "$WORK/ctx-$arch/hello.txt"
    cp "$WORK/ctx/Dockerfile" "$WORK/ctx-$arch/Dockerfile"
    "${PODMAN[@]}" build -q --no-cache --platform "linux/$arch" --manifest local/multi:1 "$WORK/ctx-$arch" \
        >> "$WORK/podman.log" 2>&1
done
"${PODMAN[@]}" manifest push --all --tls-verify=false local/multi:1 "docker://$MIMG:oci" >> "$WORK/podman.log" 2>&1
rc_oci=$?
"${PODMAN[@]}" manifest push --all --format v2s2 --tls-verify=false local/multi:1 "docker://$MIMG:docker" \
    >> "$WORK/podman.log" 2>&1
rc_docker=$?
DETAIL="podman manifest push exit $rc_oci (oci), $rc_docker (v2s2)"
check "O6.podman-multi-platform-push" test "$rc_oci" = 0 -a "$rc_docker" = 0
for kind in oci:application/vnd.oci.image.index.v1+json docker:application/vnd.docker.distribution.manifest.list.v2+json; do
    tag="${kind%%:*}"; type="${kind#*:}"
    as_admin -H "Accept: $type" "$REG/v2/oci-local/multi/manifests/$tag"
    archs="$(jq -r '[.manifests[].platform.architecture] | sort | join(",")' "$WORK/body" 2>/dev/null)"
    children_ok=0
    for child in $(jq -r '.manifests[].digest' "$WORK/body" 2>/dev/null); do
        c="$(curl -s -o /dev/null -w '%{http_code}' -u "$OCIADMIN_USER:$OCIADMIN_PASS" "$REG/v2/oci-local/multi/manifests/$child")"
        [ "$c" = 200 ] && children_ok=$((children_ok + 1))
    done
    DETAIL="Content-Type $(hdr Content-Type), platforms $archs, $children_ok children served"
    check "O6.$tag-list-served-with-its-media-type" eval '[ "$(hdr Content-Type)" = "$type" ] && [ "$archs" = amd64,arm64 ] && [ "$children_ok" = 2 ]'
done
"${PODMAN[@]}" pull -q --tls-verify=false --platform linux/arm64 "$MIMG:oci" >> "$WORK/podman.log" 2>&1
DETAIL="architecture $("${PODMAN[@]}" image inspect -f '{{.Architecture}}' "$MIMG:oci" 2>/dev/null)"
check "O6.podman-pull-selects-arm64-from-index" test "$("${PODMAN[@]}" image inspect -f '{{.Architecture}}' "$MIMG:oci" 2>/dev/null)" = arm64

# --- O2: monolithic uploads with curl ----------------------------------------------------------------------------
head -c 4096 /dev/urandom > "$WORK/mono.bin"
push_blob curl "$WORK/mono.bin"
DETAIL="POST ?digest= $CODE, Location $(hdr Location)"
check "O2.monolithic-post-201" eval '[ "$CODE" = 201 ] && [ "$(hdr Location)" = "$REG/v2/oci-local/curl/blobs/$(sha "$WORK/mono.bin")" ] && [ "$(hdr Docker-Content-Digest)" = "$(sha "$WORK/mono.bin")" ]'
as_admin "$REG/v2/oci-local/curl/blobs/$(sha "$WORK/mono.bin")"
DETAIL="GET $CODE, Content-Length $(hdr Content-Length), $(hdr Content-Type)"
check "O2.blob-get-exact-content" eval '[ "$CODE" = 200 ] && cmp -s "$WORK/body" "$WORK/mono.bin" && [ "$(hdr Content-Length)" = 4096 ] && [ "$(hdr Content-Type)" = application/octet-stream ]'
as_admin -I "$REG/v2/oci-local/curl/blobs/$(sha "$WORK/mono.bin")"
DETAIL="HEAD $CODE, Content-Length $(hdr Content-Length)"
check "O2.blob-head-size" eval '[ "$CODE" = 200 ] && [ "$(hdr Content-Length)" = 4096 ]'
as_admin -H 'Range: bytes=10-19' "$REG/v2/oci-local/curl/blobs/$(sha "$WORK/mono.bin")"
DETAIL="GET Range $CODE, $(hdr Content-Range)"
check "O2.blob-range-206" eval '[ "$CODE" = 206 ] && [ "$(hdr Content-Range)" = "bytes 10-19/4096" ] && cmp -s "$WORK/body" <(tail -c +11 "$WORK/mono.bin" | head -c 10)'

head -c 2048 /dev/urandom > "$WORK/mono2.bin"
as_admin -X POST "$REG/v2/oci-local/curl/blobs/uploads/"
LOC="$(hdr Location)"
as_admin -X PUT -H 'Content-Type: application/octet-stream' --data-binary "@$WORK/mono2.bin" "$LOC?digest=$(sha "$WORK/mono2.bin")"
DETAIL="PUT $CODE"
check "O2.post-then-put-201" eval '[ "$CODE" = 201 ] && [ "$(hdr Docker-Content-Digest)" = "$(sha "$WORK/mono2.bin")" ]'

head -c 1024 /dev/urandom > "$WORK/wrong.bin"
CLAIMED="$(sha "$WORK/mono2.bin" | sed 's/.$/0/')"
[ "$CLAIMED" != "$(sha "$WORK/mono2.bin")" ] || CLAIMED="$(sha "$WORK/mono2.bin" | sed 's/.$/1/')"
as_admin -X POST -H "$OCTET" --data-binary "@$WORK/wrong.bin" "$REG/v2/oci-local/curl/blobs/uploads/?digest=$CLAIMED"
DETAIL="POST $CODE $(err_code)"
check "O2.digest-mismatch-400-DIGEST_INVALID" eval '[ "$CODE" = 400 ] && [ "$(err_code)" = DIGEST_INVALID ]'
as_admin -I "$REG/v2/oci-local/curl/blobs/$CLAIMED"
DETAIL="HEAD claimed digest $CODE"
check "O2.mismatched-content-not-stored" test "$CODE" = 404

head -c 67108864 /dev/urandom > "$WORK/large.bin"
push_blob curl "$WORK/large.bin"
c1="$CODE"
curl -s -m 300 -u "$OCIADMIN_USER:$OCIADMIN_PASS" "$REG/v2/oci-local/curl/blobs/$(sha "$WORK/large.bin")" | sha256sum | cut -d' ' -f1 > "$WORK/large.sha"
DETAIL="POST $c1, pulled sha256 matches: $([ "sha256:$(cat "$WORK/large.sha")" = "$(sha "$WORK/large.bin")" ] && echo yes || echo no)"
check "O2.large-64MiB-blob-streamed-round-trip" test "$c1" = 201 -a "sha256:$(cat "$WORK/large.sha")" = "$(sha "$WORK/large.bin")"
rm -f "$WORK/large.bin"

# --- O3: chunked upload with curl --------------------------------------------------------------------------------
head -c 100000 /dev/urandom > "$WORK/chunked.bin"
head -c 60000 "$WORK/chunked.bin" > "$WORK/chunk1"
tail -c +60001 "$WORK/chunked.bin" > "$WORK/chunk2"
as_admin -X POST -H 'Content-Length: 0' "$REG/v2/oci-local/curl/blobs/uploads/"
LOC="$(hdr Location)"; UUID="$(hdr Docker-Upload-UUID)"
DETAIL="POST $CODE, Location $LOC, Range $(hdr Range), OCI-Chunk-Min-Length $(hdr OCI-Chunk-Min-Length)"
check "O3.post-202-session-headers" eval '[ "$CODE" = 202 ] && [ "$LOC" = "$REG/v2/oci-local/curl/blobs/uploads/$UUID" ] && [ "$(hdr Range)" = 0-0 ] && [ -n "$(hdr OCI-Chunk-Min-Length)" ]'
as_admin -X PATCH -H 'Content-Type: application/octet-stream' -H 'Content-Range: 60000-99999' \
    --data-binary "@$WORK/chunk2" "$LOC"
DETAIL="PATCH second chunk first $CODE"
check "O3.out-of-order-chunk-416" test "$CODE" = 416
as_admin -X PATCH -H 'Content-Type: application/octet-stream' -H 'Content-Range: 0-59999' \
    --data-binary "@$WORK/chunk1" "$LOC"
DETAIL="PATCH $CODE, Range $(hdr Range)"
check "O3.first-chunk-202-range" eval '[ "$CODE" = 202 ] && [ "$(hdr Range)" = 0-59999 ] && [ "$(hdr Location)" = "$LOC" ]'
as_admin "$LOC"
DETAIL="GET $CODE, Range $(hdr Range)"
check "O3.upload-status-204" eval '[ "$CODE" = 204 ] && [ "$(hdr Range)" = 0-59999 ] && [ "$(hdr Docker-Upload-UUID)" = "$UUID" ]'
as_admin -X PATCH -H 'Content-Type: application/octet-stream' -H 'Content-Range: 60000-99999' \
    --data-binary "@$WORK/chunk2" "$LOC"
DETAIL="PATCH $CODE, Range $(hdr Range)"
check "O3.second-chunk-202-range" eval '[ "$CODE" = 202 ] && [ "$(hdr Range)" = 0-99999 ]'
as_admin -X PUT -H 'Content-Length: 0' "$LOC?digest=$(sha "$WORK/chunked.bin")"
DETAIL="PUT $CODE"
check "O3.final-put-201" test "$CODE" = 201
as_admin "$REG/v2/oci-local/curl/blobs/$(sha "$WORK/chunked.bin")"
DETAIL="GET $CODE"
check "O3.assembled-blob-matches" eval '[ "$CODE" = 200 ] && cmp -s "$WORK/body" "$WORK/chunked.bin"'
as_admin "$LOC"
DETAIL="GET finished session $CODE $(err_code)"
check "O3.finished-session-gone-404" eval '[ "$CODE" = 404 ] && [ "$(err_code)" = BLOB_UPLOAD_UNKNOWN ]'

# --- O4: mount within the repository ----------------------------------------------------------------------------
as_admin -X POST "$REG/v2/oci-local/mounted/blobs/uploads/?mount=$(sha "$WORK/mono.bin")&from=oci-local/curl"
DETAIL="POST mount $CODE, Location $(hdr Location)"
check "O4.mount-same-repository-201" eval '[ "$CODE" = 201 ] && [ "$(hdr Location)" = "$REG/v2/oci-local/mounted/blobs/$(sha "$WORK/mono.bin")" ]'
as_admin "$REG/v2/oci-local/mounted/blobs/$(sha "$WORK/mono.bin")"
DETAIL="GET mounted $CODE"
check "O4.mounted-blob-served" eval '[ "$CODE" = 200 ] && cmp -s "$WORK/body" "$WORK/mono.bin"'

# --- O5: manifests by tag and digest -----------------------------------------------------------------------------
OCI_MT=application/vnd.oci.image.manifest.v1+json
as_admin -H "Accept: $OCI_MT" "$REG/v2/oci-local/hello-podman/manifests/1"
cp "$WORK/body" "$WORK/manifest.json"
MT="$(hdr Content-Type)"; MD="$(hdr Docker-Content-Digest)"
DETAIL="GET by tag $CODE, $MT, $MD"
check "O5.get-by-tag-media-type-and-digest" eval '[ "$CODE" = 200 ] && [ "$MT" = "$OCI_MT" ] && [ "$MD" = "$(sha "$WORK/manifest.json")" ] && [ "$MD" = "$PDIGEST" ]'
as_admin -I "$REG/v2/oci-local/hello-podman/manifests/$MD"
DETAIL="HEAD by digest $CODE, Content-Length $(hdr Content-Length)"
check "O5.head-by-digest-exact-length" eval '[ "$CODE" = 200 ] && [ "$(hdr Content-Length)" = "$(stat -c %s "$WORK/manifest.json")" ] && [ "$(hdr Content-Type)" = "$OCI_MT" ]'
as_admin -X PUT -H "Content-Type: $OCI_MT" --data-binary "@$WORK/manifest.json" "$REG/v2/oci-local/hello-podman/manifests/copy"
DETAIL="PUT by tag $CODE, $(hdr Docker-Content-Digest)"
check "O5.put-by-tag-201" eval '[ "$CODE" = 201 ] && [ "$(hdr Docker-Content-Digest)" = "$MD" ]'
jq -c '.layers[0].digest = "sha256:'"$(printf '%064d' 0)"'"' "$WORK/manifest.json" > "$WORK/dangling.json"
as_admin -X PUT -H "Content-Type: $OCI_MT" --data-binary "@$WORK/dangling.json" "$REG/v2/oci-local/hello-podman/manifests/dangling"
DETAIL="PUT $CODE $(err_code)"
check "O5.missing-blob-400-MANIFEST_BLOB_UNKNOWN" eval '[ "$CODE" = 400 ] && [ "$(err_code)" = MANIFEST_BLOB_UNKNOWN ]'
head -c $((4 * 1024 * 1024 + 1)) /dev/zero | tr '\0' ' ' > "$WORK/huge.json"
as_admin -X PUT -H "Content-Type: $OCI_MT" --data-binary "@$WORK/huge.json" "$REG/v2/oci-local/hello-podman/manifests/huge"
DETAIL="PUT 4 MiB + 1 $CODE $(err_code)"
check "O5.oversize-manifest-413" test "$CODE" = 413

# --- O7: tag list pagination ------------------------------------------------------------------------------------
# The manifest's blobs live under hello-podman; mount them into the new name before tagging it there.
for d in $(jq -r '.config.digest, .layers[].digest' "$WORK/manifest.json"); do
    curl -s -o /dev/null -u "$OCIADMIN_USER:$OCIADMIN_PASS" -X POST \
        "$REG/v2/oci-local/tagged/blobs/uploads/?mount=$d&from=oci-local/hello-podman"
done
for t in t1 t2 t3 t4 t5; do
    curl -s -o /dev/null -u "$OCIADMIN_USER:$OCIADMIN_PASS" -X PUT -H "Content-Type: $OCI_MT" \
        --data-binary "@$WORK/manifest.json" "$REG/v2/oci-local/tagged/manifests/$t"
done
as_admin "$REG/v2/oci-local/tagged/tags/list?n=2"
DETAIL="n=2: $(body | tr -d '\n'), Link $(hdr Link)"
check "O7.first-page-and-link" eval '[ "$CODE" = 200 ] && [ "$(jq -c .tags "$WORK/body")" = "[\"t1\",\"t2\"]" ] && [ "$(hdr Link)" = "<$REG/v2/oci-local/tagged/tags/list?n=2&last=t2>; rel=\"next\"" ]'
NEXT="$(hdr Link | sed 's/^<\([^>]*\)>.*/\1/')"
as_admin "$NEXT"
DETAIL="next: $(body | tr -d '\n')"
check "O7.next-page-follows-link" eval '[ "$(jq -c .tags "$WORK/body")" = "[\"t3\",\"t4\"]" ]'
as_admin "$REG/v2/oci-local/tagged/tags/list?n=2&last=t4"
DETAIL="last=t4: $(body | tr -d '\n'), Link '$(hdr Link)'"
check "O7.last-page-without-link" eval '[ "$(jq -c .tags "$WORK/body")" = "[\"t5\"]" ] && [ -z "$(hdr Link)" ]'
as_admin "$REG/v2/oci-local/tagged/tags/list"
DETAIL="all: $(body | tr -d '\n')"
check "O7.full-list-sorted" eval '[ "$(jq -c . "$WORK/body")" = "{\"name\":\"oci-local/tagged\",\"tags\":[\"t1\",\"t2\",\"t3\",\"t4\",\"t5\"]}" ]'

# --- O10: permissions end to end ---------------------------------------------------------------------------------
READER_AUTH="$WORK/reader-auth.json"
REGISTRY_AUTH_FILE="$READER_AUTH" "${PODMAN[@]}" login --tls-verify=false -u "$READER_USER" -p "$READER_PASS" \
    "$HOSTPORT" >> "$WORK/podman.log" 2>&1
"${PODMAN[@]}" rmi -f "$PIMG:1" >/dev/null 2>&1
REGISTRY_AUTH_FILE="$READER_AUTH" "${PODMAN[@]}" pull -q --tls-verify=false "$PIMG:1" >> "$WORK/podman.log" 2>&1
rc=$?
DETAIL="podman pull as $READER_USER exit $rc"
check "O10.reader-can-pull" test "$rc" = 0
"${PODMAN[@]}" tag "$PIMG:1" "$HOSTPORT/oci-local/by-reader:1"
REGISTRY_AUTH_FILE="$READER_AUTH" "${PODMAN[@]}" push --tls-verify=false "$HOSTPORT/oci-local/by-reader:1" \
    > "$WORK/reader-push.log" 2>&1
rc=$?
DETAIL="podman push as $READER_USER exit $rc: $(grep -io 'denied\|forbidden\|403\|unauthorized' "$WORK/reader-push.log" | head -n 1)"
check "O10.reader-podman-push-refused" test "$rc" != 0
http -u "$READER_USER:$READER_PASS" -X POST "$REG/v2/oci-local/by-reader/blobs/uploads/"
DETAIL="POST upload as $READER_USER $CODE"
check "O10.reader-push-403" test "$CODE" = 403
http -u "$READER_USER:$READER_PASS" -X DELETE "$REG/v2/oci-local/tagged/manifests/t5"
DETAIL="DELETE as $READER_USER $CODE"
check "O10.reader-delete-403" test "$CODE" = 403
head -c 512 /dev/urandom > "$WORK/deployer.bin"
http -u "$DEPLOYER_USER:$DEPLOYER_PASS" -X POST -H "$OCTET" --data-binary "@$WORK/deployer.bin" \
    "$REG/v2/oci-local/by-deployer/blobs/uploads/?digest=$(sha "$WORK/deployer.bin")"
DETAIL="POST as $DEPLOYER_USER (deploy, no delete) $CODE"
check "O10.deployer-can-push-without-delete-permission" test "$CODE" = 201
http -u "$DEPLOYER_USER:$DEPLOYER_PASS" -X DELETE "$REG/v2/oci-local/by-deployer/blobs/$(sha "$WORK/deployer.bin")"
DETAIL="DELETE as $DEPLOYER_USER $CODE"
check "O10.user-without-delete-permission-cannot-delete-403" test "$CODE" = 403
http "$REG/v2/oci-local/tagged/manifests/t1"
DETAIL="anonymous GET manifest $CODE, WWW-Authenticate: $(hdr WWW-Authenticate)"
check "O10.anonymous-pull-challenged-401" eval '[ "$CODE" = 401 ] && hdr WWW-Authenticate | grep -q "^Basic"'

# --- Isolation: path traversal, cross-repository mount, direct deploys -----------------------------------------
head -c 256 /dev/urandom > "$WORK/secret.bin"
as_admin -X POST -H "$OCTET" --data-binary "@$WORK/secret.bin" "$REG/v2/oci-other/secret/blobs/uploads/?digest=$(sha "$WORK/secret.bin")"
secret_code="$CODE"
SD="$(sha "$WORK/secret.bin")"
leaks=0; tried=""
for p in "/v2/oci-local/..%2f..%2foci-other/secret/blobs/$SD" "/v2/oci-local/../oci-other/secret/blobs/$SD" \
         "/v2/oci-local/%2e%2e/oci-other/secret/blobs/$SD" "/v2/oci-local/a/..%2F..%2F..%2Foci-other%2Fsecret/blobs/$SD" \
         "/artifactory/api/docker/oci-local/v2/..%2f..%2foci-other/secret/blobs/$SD" \
         "/artifactory/api/docker/oci-local/v2/../../oci-other/secret/blobs/$SD"; do
    http --path-as-is -u "$READER_USER:$READER_PASS" "$REG$p"
    tried="$tried $CODE"
    if [ "$CODE" = 200 ] || cmp -s "$WORK/body" "$WORK/secret.bin"; then leaks=$((leaks + 1)); fi
done
DETAIL="secret pushed to oci-other ($secret_code); traversal attempts by $READER_USER answered:$tried"
check "isolation.path-traversal-does-not-leak" test "$secret_code" = 201 -a "$leaks" = 0
http -u "$READER_USER:$READER_PASS" "$REG/v2/oci-other/secret/blobs/$SD"
DETAIL="direct GET of oci-other as $READER_USER $CODE"
check "isolation.reader-has-no-access-to-oci-other" test "$CODE" = 403
mounts=""
for from in oci-other/secret secret oci-local/../oci-other/secret; do
    http -u "$DEPLOYER_USER:$DEPLOYER_PASS" -X POST "$REG/v2/oci-local/stolen/blobs/uploads/?mount=$SD&from=$from"
    mounts="$mounts $from:$CODE"
    [ "$CODE" = 202 ] || mounts="$mounts(!)"
done
http -u "$OCIADMIN_USER:$OCIADMIN_PASS" -I "$REG/v2/oci-local/stolen/blobs/$SD"
DETAIL="mount from another repository by $DEPLOYER_USER:$mounts; blob in oci-local afterwards: $CODE"
check "isolation.cross-repository-mount-falls-back-to-202" eval '[ "$CODE" = 404 ] && ! echo "$mounts" | grep -q "(!)"'
http -u "$OCIADMIN_USER:$OCIADMIN_PASS" -X PUT -H "$OCTET" --data-binary "@$WORK/secret.bin" \
    "$BASE/oci-local/planted/_blobs/sha256/${SD#sha256:}"
DETAIL="direct generic PUT into the repository $CODE"
check "isolation.direct-deploy-refused" test "$CODE" = 403

# --- O8: deletes -------------------------------------------------------------------------------------------------
as_admin -X DELETE "$REG/v2/oci-local/tagged/manifests/t5"
DETAIL="DELETE tag $CODE"
check "O8.delete-tag-202" test "$CODE" = 202
as_admin "$REG/v2/oci-local/tagged/manifests/t5"
DETAIL="GET deleted tag $CODE"
check "O8.deleted-tag-404" test "$CODE" = 404
as_admin -X DELETE "$REG/v2/oci-local/tagged/manifests/$MD"
DETAIL="DELETE manifest by digest $CODE"
check "O8.delete-manifest-by-digest-202" test "$CODE" = 202
as_admin "$REG/v2/oci-local/tagged/manifests/$MD"
c1="$CODE"
as_admin "$REG/v2/oci-local/tagged/manifests/t1"
DETAIL="GET by digest $c1, by former tag $CODE"
check "O8.deleted-manifest-404" test "$c1" = 404 -a "$CODE" = 404
as_admin -X DELETE "$REG/v2/oci-local/curl/blobs/$(sha "$WORK/mono2.bin")"
DETAIL="DELETE blob $CODE"
check "O8.delete-blob-202" test "$CODE" = 202
as_admin "$REG/v2/oci-local/curl/blobs/$(sha "$WORK/mono2.bin")"
DETAIL="GET deleted blob $CODE"
check "O8.deleted-blob-404" test "$CODE" = 404

# --- O9: OCI distribution-spec conformance suite -----------------------------------------------------------------
if ! docker image inspect "$CONFORMANCE_IMAGE" >/dev/null 2>&1 && ! docker pull -q "$CONFORMANCE_IMAGE" >/dev/null 2>&1; then
    say "cannot pull $CONFORMANCE_IMAGE; building it from $CONFORMANCE_SRC"
    mkdir -p "$WORK/spec"
    curl -sSfL "$CONFORMANCE_SRC" | tar -xz -C "$WORK/spec" --strip-components=1 &&
        docker build -q -t rt-oci-it-conformance:v1.1.0 --build-arg VERSION=v1.1.0 "$WORK/spec/conformance" >/dev/null &&
        CONFORMANCE_IMAGE=rt-oci-it-conformance:v1.1.0
fi
CONFORMANCE_DIGEST="$(docker image inspect "$CONFORMANCE_IMAGE" --format '{{if .RepoDigests}}{{index .RepoDigests 0}}{{else}}{{.Id}}{{end}}' 2>/dev/null)"
mkdir -p "$LOG_DIR/conformance"
docker run --rm --name rt-oci-it-conformance --network host -u "$(id -u):$(id -g)" \
    -v "$LOG_DIR/conformance:/report" \
    -e OCI_ROOT_URL="$REG" -e OCI_NAMESPACE=oci-local/conformance -e OCI_CROSSMOUNT_NAMESPACE=oci-local/crossmount \
    -e OCI_USERNAME="$OCIADMIN_USER" -e OCI_PASSWORD="$OCIADMIN_PASS" \
    -e OCI_TEST_PULL=1 -e OCI_TEST_PUSH=1 -e OCI_TEST_CONTENT_DISCOVERY=1 -e OCI_TEST_CONTENT_MANAGEMENT=1 \
    -e OCI_AUTOMATIC_CROSSMOUNT=false -e OCI_HIDE_SKIPPED_WORKFLOWS=0 -e OCI_DEBUG=0 -e OCI_REPORT_DIR=/report \
    "$CONFORMANCE_IMAGE" > "$LOG_DIR/conformance/output.log" 2>&1
conformance_rc=$?
python3 -I - "$LOG_DIR/conformance/junit.xml" > "$LOG_DIR/conformance/summary.txt" 2>&1 <<'EOF'
import sys
import xml.etree.ElementTree as ET
suite = ET.parse(sys.argv[1]).getroot()
if suite.tag == 'testsuites':
    suite = suite.find('testsuite')
# Only the specs ([It] ...); the suite also reports its reporting hooks as test cases.
cases = [c for c in suite.iter('testcase') if c.get('name', '').startswith('[It]')]
failed = [c.get('name') for c in cases if c.find('failure') is not None or c.find('error') is not None]
skipped = [c.get('name') for c in cases if c.find('skipped') is not None]
print('specs=%d passed=%d failed=%d skipped=%d' % (len(cases), len(cases) - len(failed) - len(skipped), len(failed), len(skipped)))
for name in failed:
    print('FAILED ' + name)
for name in skipped:
    print('SKIPPED ' + name)
EOF
DETAIL="$CONFORMANCE_DIGEST exit $conformance_rc, $(head -n 1 "$LOG_DIR/conformance/summary.txt")"
check "O9.conformance-pull-push-discovery-management" eval '[ "$conformance_rc" = 0 ] && head -n 1 "$LOG_DIR/conformance/summary.txt" | grep -q " failed=0 "'
grep '^FAILED' "$LOG_DIR/conformance/summary.txt" >&2 || true

# --- Summary --------------------------------------------------------------------------------------------------------
echo
echo "== run-oci-it summary ($(date -u +%Y-%m-%dT%H:%M:%SZ)) stock=$STOCK_DIGEST fork=$FORK_ID"
echo "== conformance: $CONFORMANCE_DIGEST $(head -n 1 "$LOG_DIR/conformance/summary.txt" 2>/dev/null)"
for line in "${RESULTS[@]}"; do echo "$line"; done
echo "== $PASSED passed, $FAILED failed"
[ "$FAILED" = 0 ]
