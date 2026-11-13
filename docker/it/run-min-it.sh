#!/usr/bin/env bash
# Live integration test of the minimal from-scratch OCI image (design.md N3, N6): packages.oci-example, which is
# lib.mkImage with the public example plugins (plugins/oci/oci-registry.groovy and the echo-realm WASM module).
#
# TEST STAND-IN, NOT PART OF ANY BUILD OR RELEASE: the minimal image ships no JFrog Access (closed source, not
# redistributable) and expects an Access-compatible service at ACCESS_URL / ACCESS_GRPC. Until the separate
# replacement service exists, this script runs JFrog's stock image (pinned by digest) in a throwaway container
# rt-min-it-access that starts only the Access webapp bundled in it. That container exists only for the duration of
# this test, on a private docker network; nothing from it is copied into, built into or published with the image.
# Access binds its gRPC port to 127.0.0.1 only, so the stand-in runs as a sidecar: rt-min-it joins its network
# namespace (as two containers of one pod would) and uses the image's default ACCESS_URL and ACCESS_GRPC
# (localhost:8040 and localhost:8045).
#
#  1. Builds packages.oci-example with Nix and loads it with `docker load` (SKIP_BUILD=1 reuses a loaded image;
#     IMAGE_TAR loads a given archive instead).
#  2. Creates the private network rt-min-it-net and a random join key; starts rt-min-it-access (Access only, from
#     the stock image, listening on localhost) with that key, then rt-min-it (the minimal image) in the same network
#     namespace with the same join key and the default Access settings. Only Artifactory's port 8081 is published,
#     on 127.0.0.1 (the port mapping belongs to the namespace owner, rt-min-it-access).
#  3. Asserts, one PASS/FAIL line each: ping; admin login (and a wrong password refused); the image runs as a
#     non-root user; a YAML configuration PATCH creates a Generic repository; deploy and download round trip with
#     matching checksums; an Access token minted through Artifactory works as a Bearer token; the oci-registry
#     plugin is loaded, /v2/ answers 401 to anonymous and 200 to admin, and a blob and manifest push and pull round
#     trip through /v2/; the echo-realm WASM plugin is loaded and a realm Bearer token runs its echoMint execution
#     as the realm user; no web UI is served.
#
# Clients talk only to 127.0.0.1:<port> of the containers this script starts; the two containers talk to each other
# over their shared localhost. No address range is probed.
#
# Containers are named rt-min-it*, the volumes rt-min-it-var and rt-min-it-access-var, the network rt-min-it-net;
# leftovers with those names are removed first, and everything is removed again on exit.
#
# Usage: docker/it/run-min-it.sh   (env: SKIP_BUILD=1, IMAGE, IMAGE_TAR, LOG_DIR keeps container logs, TMPDIR)
# Exit status: 0 when every assertion passes, 1 otherwise.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
VERSION="$(cat "$ROOT/nix/version.txt")"
IMAGE="${IMAGE:-containercontainer:$VERSION-example}"
# The stock image, used here only as the source of the Access test stand-in (digest as in design.md B2).
STOCK_IMAGE=releases-docker.jfrog.io/jfrog/artifactory-oss@sha256:d103c0464d33645acdcbfbb6fc61310d7faf14c9893975cbee562487eaeac1d1
NAME=rt-min-it
ACCESS=rt-min-it-access
NET=rt-min-it-net
VOLUME=rt-min-it-var
ACCESS_VOLUME=rt-min-it-access-var
VAR=/var/opt/jfrog/artifactory
ADMIN=admin:password
ISSUER=echo-it
STARTUP_TIMEOUT=600
LOG_DIR="${LOG_DIR:-$(mktemp -d "${TMPDIR:-/tmp}/rt-min-it-logs.XXXXXX")}"
WORK="$(mktemp -d "${TMPDIR:-/tmp}/rt-min-it-work.XXXXXX")"
mkdir -p "$LOG_DIR"

PASSED=0
FAILED=0
DETAIL=""

say() { echo "run-min-it: $*" >&2; }
record() { # record <PASS|FAIL> <name> <detail>
    if [ "$1" = PASS ]; then PASSED=$((PASSED + 1)); else FAILED=$((FAILED + 1)); fi
    echo "$1 $2${3:+ - $3}"
}
check() { # check <name> <condition-command...>; the detail is taken from $DETAIL
    local name="$1"; shift
    if "$@"; then record PASS "$name" "${DETAIL:-}"; else record FAIL "$name" "${DETAIL:-}"; fi
    DETAIL=""
}

remove_all() {
    docker rm -f "$NAME" "$ACCESS" >/dev/null 2>&1 || true
    docker volume rm "$VOLUME" "$ACCESS_VOLUME" >/dev/null 2>&1 || true
    docker network rm "$NET" >/dev/null 2>&1 || true
}
cleanup() {
    local rc=$?
    for c in "$NAME" "$ACCESS"; do
        docker container inspect "$c" >/dev/null 2>&1 && docker logs "$c" > "$LOG_DIR/$c.log" 2>&1
    done
    docker exec "$NAME" sh -c "cat $VAR/log/artifactory-service.log" > "$LOG_DIR/artifactory-service.log" 2>&1 \
        || true
    remove_all
    rm -rf "$WORK" 2>/dev/null || true
    say "logs kept in $LOG_DIR"
    exit "$rc"
}
trap cleanup EXIT
trap 'exit 130' INT TERM

# --- HTTP helpers (127.0.0.1 only). Each sets CODE, writes the body to $WORK/body and the headers to $WORK/headers.
http() { CODE="$(curl -s -m 120 -D "$WORK/headers" -o "$WORK/body" -w '%{http_code}' "$@")" || CODE=000; }
as_admin() { http -u "$ADMIN" "$@"; }
hdr() { grep -i "^$1:" "$WORK/headers" | head -1 | cut -d: -f2- | tr -d '\r' | sed 's/^ *//'; }
b64url() { base64 -w0 | tr '+/' '-_' | tr -d '='; }
realm_token() { # realm_token <sub>: an unsigned JWT-shaped token the echo-realm example accepts (insecure by design)
    printf '%s.%s.c2ln' "$(printf '{"alg":"none","typ":"JWT"}' | b64url)" \
        "$(printf '{"iss":"%s","sub":"%s","exp":%s}' "$ISSUER" "$1" "$(( $(date +%s) + 3600 ))" | b64url)"
}
sha() { echo "sha256:$(sha256sum "$1" | cut -d' ' -f1)"; }

wait_for_ping() { # bounded by STARTUP_TIMEOUT
    local waited=0
    while [ "$waited" -lt "$STARTUP_TIMEOUT" ]; do
        for c in "$NAME" "$ACCESS"; do
            if [ "$(docker inspect -f '{{.State.Running}}' "$c" 2>/dev/null)" != true ]; then
                say "$c is not running"; return 1
            fi
        done
        http "$BASE/api/system/ping"
        if [ "$CODE" = 200 ] && [ "$(cat "$WORK/body")" = OK ]; then say "$NAME answered ping after ${waited}s"; return 0; fi
        sleep 5; waited=$((waited + 5))
    done
    say "$NAME did not answer ping within ${STARTUP_TIMEOUT}s"; return 1
}

# --- Setup --------------------------------------------------------------------------------------------------------
for tool in docker curl jq ss base64 openssl sha256sum; do
    command -v "$tool" >/dev/null || { say "missing tool: $tool"; exit 1; }
done

leftovers="$(docker ps -a --filter "name=^rt-min-it" --format '{{.Names}}')"
[ -z "$leftovers" ] || say "removing leftovers: $leftovers"
remove_all

# Pick the first port from 18240 with no local listener (reads this host's socket table; sends no traffic).
PORT=18240
while ss -ltnH "sport = :$PORT" | grep -q .; do
    PORT=$((PORT + 1))
    [ "$PORT" -le 18259 ] || { say "no free port in 18240-18259 on this host"; exit 1; }
done
REG="http://127.0.0.1:$PORT"
BASE="$REG/artifactory"
say "using $BASE"

if [ -n "${IMAGE_TAR:-}" ]; then
    docker load -i "$IMAGE_TAR" >/dev/null || { say "docker load $IMAGE_TAR failed"; exit 1; }
elif [ "${SKIP_BUILD:-0}" != 1 ]; then
    command -v nix >/dev/null || { say "missing tool: nix (or set IMAGE_TAR / SKIP_BUILD=1)"; exit 1; }
    nix build -L "$ROOT#oci-example" --out-link "$WORK/oci" || { say "nix build .#oci-example failed"; exit 1; }
    docker load -i "$WORK"/oci/*.oci.tar >/dev/null || { say "docker load failed"; exit 1; }
fi
docker image inspect "$IMAGE" >/dev/null || { say "no image $IMAGE"; exit 1; }
docker image inspect "$STOCK_IMAGE" >/dev/null 2>&1 || docker pull -q "$STOCK_IMAGE" >/dev/null \
    || { say "cannot pull the Access stand-in image"; exit 1; }
say "minimal image $(docker image inspect "$IMAGE" --format '{{.Id}}')"

JOIN_KEY="$(openssl rand -hex 32)"
docker network create "$NET" >/dev/null
docker volume create "$VOLUME" >/dev/null
docker volume create "$ACCESS_VOLUME" >/dev/null

# The Access stand-in: the stock image's bundled Access webapp in its own Tomcat on 8040 (HTTP) and 8045 (gRPC),
# nothing else from that image started (no router, no Artifactory, no frontend).
cat > "$WORK/access.sh" <<'EOF'
set -eu
H=/opt/jfrog/artifactory; V=$H/var; APP=$H/app; T=$APP/access/tomcat
mkdir -p "$V/etc/security" "$V/etc/access/tomcat/conf" "$V/work/access/tomcat/temp" "$V/log"
printf '%s' "$JOIN_KEY" > "$V/etc/security/join.key"
[ -s "$V/etc/security/master.key" ] || od -An -tx1 -N32 /dev/urandom | tr -d ' \n' > "$V/etc/security/master.key"
cat > "$V/etc/system.yaml" <<YAML
configVersion: 1
shared:
  node:
    id: rt-min-it-access
    ip: 127.0.0.1
  database:
    allowNonPostgresql: true
router:
  enabled: false
access:
  http:
    port: 8040
  grpc:
    port: 8045
YAML
cat > "$V/etc/access/tomcat/conf/server.xml" <<XML
<Server port="-1" shutdown="SHUTDOWN"><Service name="Catalina">
 <Connector port="8040" address="localhost" maxThreads="50" sendReasonPhrase="false" encodedSolidusHandling="decode"/>
 <Engine name="Catalina" defaultHost="localhost"><Host name="localhost" workDir="$V/work/access/tomcat" appBase="webapps" startStopThreads="1"/></Engine>
</Service></Server>
XML
exec "$APP/third-party/java/bin/java" -Xmx768m -XX:+UseG1GC \
  --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.lang.reflect=ALL-UNNAMED \
  --add-opens=java.base/java.io=ALL-UNNAMED --add-opens=java.base/java.util=ALL-UNNAMED \
  --add-opens=java.base/java.util.concurrent=ALL-UNNAMED --add-opens=java.base/java.lang.invoke=ALL-UNNAMED \
  --add-opens=java.base/java.text=ALL-UNNAMED --add-opens=java.base/java.nio=ALL-UNNAMED \
  --add-opens=java.rmi/sun.rmi.transport=ALL-UNNAMED --enable-native-access=ALL-UNNAMED \
  -Djava.util.logging.manager=org.apache.juli.ClassLoaderLogManager \
  -Djava.util.logging.config.file=$T/conf/logging.properties -Dfile.encoding=UTF8 \
  -Djava.security.egd=file:/dev/./urandom -Djf.product.home=$H \
  -classpath $T/bin/bootstrap.jar:$T/bin/tomcat-juli.jar -Dcatalina.base=$T -Dcatalina.home=$T \
  -Djava.io.tmpdir=$V/work/access/tomcat/temp org.apache.catalina.startup.Bootstrap start
EOF
chmod 0644 "$WORK/access.sh"
docker run -d --name "$ACCESS" --network "$NET" -p "127.0.0.1:$PORT:8081" -e JOIN_KEY="$JOIN_KEY" \
    -v "$ACCESS_VOLUME:$VAR" -v "$WORK/access.sh:/rt-min-it-access.sh:ro" --entrypoint bash "$STOCK_IMAGE" \
    /rt-min-it-access.sh >/dev/null

# The minimal image with its defaults (ACCESS_URL http://localhost:8040/access, ACCESS_GRPC localhost:8045).
docker run -d --name "$NAME" --network "container:$ACCESS" -v "$VOLUME:$VAR" \
    -e JF_SHARED_SECURITY_JOINKEY="$JOIN_KEY" "$IMAGE" >/dev/null
wait_for_ping || { DETAIL="see $LOG_DIR"; check "setup.artifactory-starts" false; exit 1; }
DETAIL="GET /api/system/ping OK"
check "N3.ping" true

# --- Image basics -------------------------------------------------------------------------------------------------
uid="$(docker exec "$NAME" id -u)"
user="$(docker image inspect "$IMAGE" --format '{{.Config.User}}')"
DETAIL="process uid $uid, image user $user"
check "N6.runs-as-non-root" eval '[ "$uid" = 1030 ] && [ "$user" = 1030:1030 ]'

as_admin "$BASE/api/system/version"; v="$CODE"; version="$(jq -r '.version // empty' "$WORK/body" 2>/dev/null)"
http -u admin:wrong-password "$BASE/api/system/version"; wrong="$CODE"
DETAIL="admin $v (version $version), wrong password $wrong"
check "N3.admin-login" eval '[ "$v" = 200 ] && [ "$wrong" = 401 ]'

# --- Repository, deploy and download -------------------------------------------------------------------------------
printf 'localRepositories:\n  min-generic:\n    type: generic\n  oci-local:\n    type: generic\n    pluginPackageType: oci-registry\n' \
    > "$WORK/repos.yaml"
as_admin -X PATCH -H 'Content-Type: application/yaml' --data-binary "@$WORK/repos.yaml" "$BASE/api/system/configuration"
p="$CODE"
as_admin "$BASE/api/repositories?type=local"
repo="$(jq -c '.[] | select(.key == "min-generic") | {key, type, packageType}' "$WORK/body" 2>/dev/null)"
DETAIL="PATCH $p, listed as $repo"
check "N3.yaml-patch-creates-generic-repository" eval '[ "$p" = 200 ] && [ "$CODE" = 200 ] && jq -e ".type == \"LOCAL\" and (.packageType | ascii_downcase) == \"generic\"" <<< "$repo" >/dev/null'

head -c 300000 /dev/urandom > "$WORK/file.bin"
as_admin -T "$WORK/file.bin" "$BASE/min-generic/a/file.bin"; put="$CODE"
as_admin "$BASE/api/storage/min-generic/a/file.bin"
stored_sha="$(jq -r '.checksums.sha256 // empty' "$WORK/body" 2>/dev/null)"
as_admin "$BASE/min-generic/a/file.bin"; get="$CODE"; cp "$WORK/body" "$WORK/got.bin"
same="$(cmp -s "$WORK/file.bin" "$WORK/got.bin" && echo identical || echo different)"
DETAIL="PUT $put, GET $get ($same), stored sha256 ${stored_sha:0:12}..."
check "N3.deploy-download-round-trip" eval '[ "$put" = 201 ] && [ "$get" = 200 ] && [ "$same" = identical ] && [ "sha256:$stored_sha" = "$(sha "$WORK/file.bin")" ]'

# --- Access tokens --------------------------------------------------------------------------------------------------
as_admin -X POST -d username=admin -d scope=member-of-groups:readers -d expires_in=600 "$BASE/api/security/token"
mint="$CODE"; TOKEN="$(jq -r '.access_token // empty' "$WORK/body" 2>/dev/null)"
http -H "Authorization: Bearer $TOKEN" "$BASE/api/repositories"; use="$CODE"
http -H "Authorization: Bearer ${TOKEN}x" "$BASE/api/repositories/min-generic"; bad="$CODE"
DETAIL="mint $mint (token ${#TOKEN} chars$([ -n "$TOKEN" ] || printf ', %s' "$(tr -d '\n' < "$WORK/body" | head -c 300)")), Bearer use $use, tampered token $bad"
check "N3.token-mint-and-bearer-use" eval '[ "$mint" = 200 ] && [ -n "$TOKEN" ] && [ "$use" = 200 ] && [ "$bad" = 401 ]'

# --- Plugins: Groovy OCI registry -----------------------------------------------------------------------------------
as_admin "$BASE/api/plugins"
cp "$WORK/body" "$WORK/plugins.json"
DETAIL="GET /api/plugins $CODE: $(jq -c '.plugins | map_values(.status)' "$WORK/plugins.json" 2>/dev/null)"
check "N3.oci-registry-plugin-loaded" eval '[ "$CODE" = 200 ] && jq -e ".plugins[\"oci-registry\"].status == \"Loaded\"" "$WORK/plugins.json" >/dev/null'
http "$REG/v2/"; anon="$CODE"; challenge="$(hdr WWW-Authenticate)"
as_admin "$REG/v2/"; adm="$CODE"; api="$(hdr Docker-Distribution-Api-Version)"
DETAIL="anonymous $anon ($challenge), admin $adm ($api)"
check "N3.v2-401-anonymous-200-admin" eval '[ "$anon" = 401 ] && [ "$adm" = 200 ] && [ "$api" = registry/2.0 ]'

printf '{}' > "$WORK/cfg"; head -c 4096 /dev/urandom > "$WORK/layer"
pushed=0
for b in cfg layer; do
    as_admin -X POST -H 'Content-Type: application/octet-stream' --data-binary "@$WORK/$b" \
        "$REG/v2/oci-local/min/blobs/uploads/?digest=$(sha "$WORK/$b")"
    [ "$CODE" = 201 ] && pushed=$((pushed + 1))
done
printf '{"schemaVersion":2,"mediaType":"application/vnd.oci.image.manifest.v1+json","config":{"mediaType":"application/vnd.oci.image.config.v1+json","digest":"%s","size":2},"layers":[{"mediaType":"application/vnd.oci.image.layer.v1.tar","digest":"%s","size":4096}]}' \
    "$(sha "$WORK/cfg")" "$(sha "$WORK/layer")" > "$WORK/manifest.json"
as_admin -X PUT -H 'Content-Type: application/vnd.oci.image.manifest.v1+json' --data-binary "@$WORK/manifest.json" \
    "$REG/v2/oci-local/min/manifests/v1"; mput="$CODE"
as_admin -H 'Accept: application/vnd.oci.image.manifest.v1+json' "$REG/v2/oci-local/min/manifests/v1"
mget="$CODE"; cp "$WORK/body" "$WORK/got-manifest.json"
as_admin "$REG/v2/oci-local/min/blobs/$(sha "$WORK/layer")"; bget="$CODE"; cp "$WORK/body" "$WORK/got-layer"
msame="$(cmp -s "$WORK/manifest.json" "$WORK/got-manifest.json" && echo identical || echo different)"
bsame="$(cmp -s "$WORK/layer" "$WORK/got-layer" && echo identical || echo different)"
DETAIL="blobs pushed $pushed/2, manifest PUT $mput GET $mget ($msame), blob GET $bget ($bsame)"
check "N3.oci-push-pull-round-trip" eval '[ "$pushed" = 2 ] && [ "$mput" = 201 ] && [ "$mget" = 200 ] && [ "$bget" = 200 ] && [ "$msame$bsame" = identicalidentical ]'

# --- Plugins: WASM echo-realm ---------------------------------------------------------------------------------------
DETAIL="echo-realm: $(jq -c '.plugins["echo-realm"] // empty' "$WORK/plugins.json" 2>/dev/null)"
check "N3.echo-realm-wasm-plugin-loaded" eval 'jq -e ".plugins[\"echo-realm\"].status == \"Loaded\" and .plugins[\"echo-realm\"].realms == \"echo\"" "$WORK/plugins.json" >/dev/null'
http -H "Authorization: Bearer $(realm_token ok:carol)" -X POST "$BASE/api/plugins/execute/echoMint"
caller="$(jq -r '.caller // empty' "$WORK/body" 2>/dev/null)"; minted="$(jq -r '.access_token // empty' "$WORK/body" 2>/dev/null)"
c="$CODE"
http -H "Authorization: Bearer $minted" "$BASE/api/storage/min-generic"; used="$CODE"
http -H "Authorization: Bearer $(realm_token reject:x)" -X POST "$BASE/api/plugins/execute/echoMint"; rejected="$CODE"
DETAIL="echoMint $c as '$caller', minted token use $used, rejected token $rejected"
check "N3.echo-realm-bearer-login-runs-execution" eval '[ "$c" = 200 ] && [ "$caller" = carol ] && [ -n "$minted" ] && [ "$used" = 200 ] && [ "$rejected" = 401 ]'

# --- No web UI ------------------------------------------------------------------------------------------------------
http "$BASE/webapp/"; webapp="$CODE"
http "$REG/ui/"; ui="$CODE"
DETAIL="/artifactory/webapp/ $webapp, /ui/ $ui"
check "N6.no-web-ui" eval '[ "$webapp" != 200 ] && [ "$ui" != 200 ]'

echo
say "$PASSED passed, $FAILED failed"
[ "$FAILED" = 0 ]
