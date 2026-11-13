#!/usr/bin/env bash
# Live integration test of WASM plugins (design.md W1-W7) on the overlay image (docker/build-overlay.sh) of the stock
# Artifactory OSS 7.161.26 image, with the public example module plugins/wasm-examples/echo-realm.
#
#  1. Builds the overlay image (SKIP_BUILD=1 reuses it; IMAGE overrides).
#  2. Creates a throwaway CA and an HTTPS certificate for stub.wasm-it.test with the host's openssl, and serves
#     https://stub.wasm-it.test:8443/allow (body "allow") from nginx in container rt-wasm-it-stub on a private
#     network rt-wasm-it-net. The CA goes to var/etc/security/keys/trusted/, which the image's startup imports
#     into the JVM trust store; the WASM host has no switch to skip TLS verification.
#  3. Seeds the committed build of the example module (backend/core/src/test/resources/wasm/echo-realm.wasm) and a
#     manifest allowing only stub.wasm-it.test:8443 into a fresh volume, starts Artifactory on the private network
#     published on 127.0.0.1 only, claims the server with the setup token it wrote (docker/it/initial-admin.sh: a
#     random administrator password kept only in this shell), and creates a generic repository as admin.
#     Embedded access seeds no readers group or Anything permission target (stock Access does) and OSS has no API
#     to create groups, so the realm user gets read on that repository from a permission target created here, and
#     the JIT group check compares against the groups that actually exist.
#  4. Asserts, one PASS/FAIL line each: the plugin is loaded; a Bearer token (unsigned, JWT-shaped, issuer echo-it;
#     the example is insecure by design) logs in through the WASM realm and runs the echoMint execution as that user;
#     the just-in-time user lives in realm plugin:echo with only the existing non-admin group; the minted token is a
#     user-scoped Access token within the requested lifetime that works; a fetch: token makes the module call the
#     stub over HTTPS (http_get effect) before it is accepted; a token naming admin is refused (P3); a looping
#     module is killed at the step deadline and fails closed (401) as does a trapping one, and the plugin keeps
#     working; passwords keep working; after the plugin files are removed and plugins reloaded, its tokens are
#     refused and passwords still work.
#
# Clients talk only to 127.0.0.1:<port> of the container this script starts; Artifactory talks only to the stub
# container. No address range is probed.
#
# Containers are named rt-wasm-it*, the volume rt-wasm-it-var, the network rt-wasm-it-net; leftovers with those
# names are removed first, and everything is removed again on exit.
#
# Usage: docker/it/run-wasm-it.sh   (env: SKIP_BUILD=1, IMAGE, LOG_DIR keeps container logs, TMPDIR)
# Exit status: 0 when every assertion passes, 1 otherwise.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
IMAGE="${IMAGE:-artifactory-oss-fork:7.161.26-wasm-it}"
STUB_IMAGE="${STUB_IMAGE:-nginx:1.27-alpine}"
NAME=rt-wasm-it
STUB=rt-wasm-it-stub
NET=rt-wasm-it-net
VOLUME=rt-wasm-it-var
VAR=/var/opt/jfrog/artifactory
PLUGINS_DIR=$VAR/etc/artifactory/plugins
TRUSTED_DIR=$VAR/etc/security/keys/trusted
MODULE="$ROOT/backend/core/src/test/resources/wasm/echo-realm.wasm"
STUB_HOST=stub.wasm-it.test
ISSUER=echo-it
# No default administrator credential exists (design IA1): ia_claim_server sets ADMIN after claiming the server.
# shellcheck source=initial-admin.sh
. "$ROOT/docker/it/initial-admin.sh"
ADMIN=""
MINT_SECONDS=300
STARTUP_TIMEOUT=600
LOG_DIR="${LOG_DIR:-$(mktemp -d "${TMPDIR:-/tmp}/rt-wasm-it-logs.XXXXXX")}"
WORK="$(mktemp -d "${TMPDIR:-/tmp}/rt-wasm-it-work.XXXXXX")"
mkdir -p "$LOG_DIR" "$WORK/stub"

PASSED=0
FAILED=0
DETAIL=""

say() { echo "run-wasm-it: $*" >&2; }
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
    docker rm -f "$NAME" "$STUB" rt-wasm-it-seed >/dev/null 2>&1 || true
    docker volume rm "$VOLUME" >/dev/null 2>&1 || true
    docker network rm "$NET" >/dev/null 2>&1 || true
}
cleanup() {
    local rc=$?
    if docker container inspect "$NAME" >/dev/null 2>&1; then
        docker logs "$NAME" > "$LOG_DIR/$NAME.log" 2>&1 || true
        docker exec "$NAME" sh -c "cat $VAR/log/artifactory-service.log" > "$LOG_DIR/artifactory-service.log" 2>&1 \
            || true
    fi
    docker logs "$STUB" > "$LOG_DIR/$STUB.log" 2>&1 || true
    remove_all
    rm -rf "$WORK" 2>/dev/null || true
    say "logs kept in $LOG_DIR"
    exit "$rc"
}
trap cleanup EXIT
trap 'exit 130' INT TERM

# --- HTTP helpers (127.0.0.1 only). Each sets CODE and writes the body to $WORK/body.
http() { CODE="$(curl -s -m 120 -o "$WORK/body" -w '%{http_code}' "$@")" || CODE=000; }
as_admin() { http -u "$ADMIN" "$@"; }
bearer() { local token="$1"; shift; http -H "Authorization: Bearer $token" "$@"; }
b64url() { base64 -w0 | tr '+/' '-_' | tr -d '='; }
token() { # token <sub>: an unsigned JWT-shaped token the example accepts
    printf '%s.%s.c2ln' "$(printf '{"alg":"none","typ":"JWT"}' | b64url)" \
        "$(printf '{"iss":"%s","sub":"%s","exp":%s}' "$ISSUER" "$1" "$(( $(date +%s) + 3600 ))" | b64url)"
}
claims_b64() { cut -d. -f2 <<< "$1" | tr '_-' '/+' | awk '{ l = length($0) % 4; if (l) $0 = $0 substr("===", l); print }' | base64 -d 2>/dev/null; }
mint_as() { # mint_as <sub>: run echoMint with a realm token; the response is in $WORK/body
    bearer "$(token "$1")" -X POST "$BASE/api/plugins/execute/echoMint"
}

wait_for_ping() { # bounded by STARTUP_TIMEOUT
    local waited=0
    while [ "$waited" -lt "$STARTUP_TIMEOUT" ]; do
        if [ "$(docker inspect -f '{{.State.Running}}' "$NAME" 2>/dev/null)" != true ]; then
            say "$NAME is not running"; return 1
        fi
        http "$BASE/api/system/ping"
        if [ "$CODE" = 200 ] && [ "$(cat "$WORK/body")" = OK ]; then say "$NAME answered ping after ${waited}s"; return 0; fi
        sleep 5; waited=$((waited + 5))
    done
    say "$NAME did not answer ping within ${STARTUP_TIMEOUT}s"; return 1
}

# --- Setup --------------------------------------------------------------------------------------------------------
[ -f "$MODULE" ] || { say "missing $MODULE"; exit 1; }
for tool in docker curl jq ss base64 openssl; do
    command -v "$tool" >/dev/null || { say "missing tool: $tool"; exit 1; }
done

leftovers="$(docker ps -a --filter "name=^rt-wasm-it" --format '{{.Names}}')"
[ -z "$leftovers" ] || say "removing leftovers: $leftovers"
remove_all

# Pick the first port from 18222 with no local listener (reads this host's socket table; sends no traffic).
PORT=18222
while ss -ltnH "sport = :$PORT" | grep -q .; do
    PORT=$((PORT + 1))
    [ "$PORT" -le 18239 ] || { say "no free port in 18222-18239 on this host"; exit 1; }
done
BASE="http://127.0.0.1:$PORT/artifactory"
say "using $BASE"

if [ "${SKIP_BUILD:-0}" != 1 ]; then
    "$ROOT/docker/build-overlay.sh" "$IMAGE" || { say "overlay build failed"; exit 1; }
fi
docker image inspect "$IMAGE" >/dev/null || { say "no image $IMAGE"; exit 1; }
docker image inspect "$STUB_IMAGE" >/dev/null 2>&1 || docker pull -q "$STUB_IMAGE" >/dev/null
say "fork image $(docker image inspect "$IMAGE" --format '{{.Id}}')"

# Throwaway CA and server certificate for the stub.
openssl req -x509 -newkey rsa:2048 -nodes -days 2 -subj "/CN=rt-wasm-it CA" -keyout "$WORK/stub/ca.key" \
    -out "$WORK/stub/ca.pem" -addext "basicConstraints=critical,CA:TRUE" -addext "keyUsage=critical,keyCertSign" \
    >/dev/null 2>&1 || { say "CA creation failed"; exit 1; }
openssl req -newkey rsa:2048 -nodes -subj "/CN=$STUB_HOST" -keyout "$WORK/stub/server.key" \
    -out "$WORK/stub/server.csr" >/dev/null 2>&1
printf 'subjectAltName=DNS:%s\nextendedKeyUsage=serverAuth\n' "$STUB_HOST" > "$WORK/stub/ext.cnf"
openssl x509 -req -in "$WORK/stub/server.csr" -CA "$WORK/stub/ca.pem" -CAkey "$WORK/stub/ca.key" -CAcreateserial \
    -days 2 -extfile "$WORK/stub/ext.cnf" -out "$WORK/stub/server.pem" >/dev/null 2>&1 \
    || { say "certificate creation failed"; exit 1; }
chmod 644 "$WORK/stub/server.key"
cat > "$WORK/stub/default.conf" <<EOF
server {
    listen 8443 ssl;
    server_name $STUB_HOST;
    ssl_certificate /stub/server.pem;
    ssl_certificate_key /stub/server.key;
    location = /allow { default_type text/plain; return 200 "allow\n"; }
    location / { return 404; }
}
EOF
docker network create "$NET" >/dev/null
docker run -d --name "$STUB" --network "$NET" --network-alias "$STUB_HOST" -v "$WORK/stub:/stub:ro" \
    -v "$WORK/stub/default.conf:/etc/nginx/conf.d/default.conf:ro" "$STUB_IMAGE" >/dev/null

cat > "$WORK/echo-realm.wasm.yaml" <<EOF
abi: 1
realms:
  - {name: echo, policy: exclusive, autoCreateUsers: true, hooks: [bearer, authenticate]}
executions:
  - {name: echoMint, users: ['*'], groups: [], httpMethod: POST}
capabilities:
  httpGet: {hosts: ['$STUB_HOST:8443']}
limits: {memoryPages: 256, stepTimeoutMs: 2000, maxSteps: 4, httpTimeoutMs: 5000}
config:
  issuer: $ISSUER
  groups: [readers, wasm-it-ghost]
  fetch_url: https://$STUB_HOST:8443/allow
  mint_seconds: $MINT_SECONDS
EOF

docker volume create "$VOLUME" >/dev/null
seed() { # seed <target path> < content
    docker run --rm -i --name rt-wasm-it-seed --network none -v "$VOLUME:$VAR" --entrypoint sh "$IMAGE" \
        -c "mkdir -p \"\$(dirname '$1')\" && cat > '$1'"
}
seed "$PLUGINS_DIR/echo-realm.wasm" < "$MODULE"
seed "$PLUGINS_DIR/echo-realm.wasm.yaml" < "$WORK/echo-realm.wasm.yaml"
seed "$TRUSTED_DIR/rt-wasm-it-ca.pem" < "$WORK/stub/ca.pem"
docker run -d --name "$NAME" --network "$NET" -e JF_SHARED_DATABASE_ALLOWNONPOSTGRESQL=true \
    -p "127.0.0.1:$PORT:8081" -v "$VOLUME:$VAR" "$IMAGE" >/dev/null
wait_for_ping || { DETAIL="see $LOG_DIR/$NAME.log"; check "setup.artifactory-starts" false; exit 1; }
ia_claim_server "$NAME" "$BASE" || { DETAIL="see $LOG_DIR/$NAME.log"; check "setup.initial-admin-claim" false; exit 1; }

as_admin "$BASE/api/plugins"
DETAIL="GET /api/plugins $CODE: $(jq -c '.plugins["echo-realm"] // empty' "$WORK/body" 2>/dev/null)"
check "W1.wasm-plugin-loaded-and-listed" eval '[ "$CODE" = 200 ] && jq -e ".plugins[\"echo-realm\"].status == \"Loaded\" and .plugins[\"echo-realm\"].realms == \"echo\" and .plugins[\"echo-realm\"].executions == \"echoMint\"" "$WORK/body" >/dev/null'

printf 'localRepositories:\n  wasm-generic:\n    type: generic\n' > "$WORK/repos.yaml"
as_admin -X PATCH -H 'Content-Type: application/yaml' --data-binary "@$WORK/repos.yaml" "$BASE/api/system/configuration"
DETAIL="PATCH $CODE"
check "setup.repository-created" test "$CODE" = 200

# --- W2/W3: bearer login through the WASM realm, execution and mint ---------------------------------------------
mint_as ok:carol
cp "$WORK/body" "$WORK/mint.json"
ACCESS_TOKEN="$(jq -r '.access_token // empty' "$WORK/mint.json" 2>/dev/null)"
EXPIRES_IN="$(jq -r '.expires_in // 0' "$WORK/mint.json" 2>/dev/null)"
DETAIL="POST $CODE, caller $(jq -r '.caller // empty' "$WORK/mint.json" 2>/dev/null), expires_in $EXPIRES_IN"
check "W2.bearer-login-runs-the-execution-as-the-realm-user" eval '[ "$CODE" = 200 ] && [ "$(jq -r .caller "$WORK/mint.json")" = carol ]'

as_admin -H 'X-Requested-With: XMLHttpRequest' "$BASE/ui/users/carol"
cp "$WORK/body" "$WORK/carol.json"
user_code="$CODE"
JIT_GROUPS="$(jq -c '[(.groups // [])[] | if type == "object" then .groupName // .name else . end] | sort' "$WORK/carol.json" 2>/dev/null)"
# The manifest assigns [readers, wasm-it-ghost]; only the groups that exist may be given (wasm-it-ghost never does).
as_admin -H 'X-Requested-With: XMLHttpRequest' "$BASE/ui/users/groups"
EXPECTED_GROUPS="$(jq -c '[.[] | if type == "object" then .groupName // .name else . end | select(. == "readers" or . == "wasm-it-ghost")] | sort' "$WORK/body" 2>/dev/null)"
DETAIL="GET $user_code: realm $(jq -r '.realm // empty' "$WORK/carol.json"), groups $JIT_GROUPS (existing of the manifest's: $EXPECTED_GROUPS), admin $(jq -r '.admin' "$WORK/carol.json")"
check "P3.jit-user-in-plugin-realm-with-only-existing-non-admin-groups" eval '[ "$user_code" = 200 ] && [ -n "$EXPECTED_GROUPS" ] && [ "$JIT_GROUPS" = "$EXPECTED_GROUPS" ] && [ "$(jq -r .admin "$WORK/carol.json")" = false ] && [ "$(jq -r .realm "$WORK/carol.json")" = "plugin:echo" ]'

AT_CLAIMS="$(claims_b64 "$ACCESS_TOKEN")"
LIFETIME=$(( $(jq -r '.exp // 0' <<< "$AT_CLAIMS" 2>/dev/null) - $(jq -r '.iat // 0' <<< "$AT_CLAIMS" 2>/dev/null) ))
DETAIL="scope $(jq -r '.scp // empty' <<< "$AT_CLAIMS" 2>/dev/null), lifetime ${LIFETIME}s, expires_in $EXPIRES_IN"
check "W3.mint-effect-gives-a-user-scoped-token-within-the-requested-lifetime" eval '[ -n "$ACCESS_TOKEN" ] && [ "$(jq -r .scp <<< "$AT_CLAIMS")" = "applied-permissions/user" ] && [ "$EXPIRES_IN" -gt 0 ] && [ "$EXPIRES_IN" -le "$MINT_SECONDS" ] && [ "$LIFETIME" -le "$MINT_SECONDS" ]'
as_admin -X POST -H 'Content-Type: application/json' -H 'X-Requested-With: XMLHttpRequest' \
    -d '{"name":"wasm-it-carol","repoPermission":{"repoKeys":["wasm-generic"],"includePatterns":["**"],"excludePatterns":[],"userPermissionActions":[{"principal":"carol","actions":["read"]}],"groupPermissionActions":[]}}' \
    "$BASE/ui/permissiontargets"; perm="$CODE"
bearer "$ACCESS_TOKEN" "$BASE/api/storage/wasm-generic"
DETAIL="read permission for carol $perm, GET with the minted token $CODE"
check "W3.minted-token-works" test "$perm" = 201 -a "$CODE" = 200

before="$(docker logs "$STUB" 2>&1 | grep -c 'GET /allow')"
mint_as fetch:dave
c="$CODE"; who="$(jq -r '.caller // empty' "$WORK/body" 2>/dev/null)"
after="$(docker logs "$STUB" 2>&1 | grep -c 'GET /allow')"
DETAIL="POST $c as $who, stub GET /allow $before -> $after"
check "W3.http-get-effect-reaches-the-allowlisted-https-host" eval '[ "$c" = 200 ] && [ "$who" = dave ] && [ $((after - before)) -ge 1 ]'

# --- P3, W4: refusals and fail-closed failures ------------------------------------------------------------------
mint_as ok:admin; a="$CODE"
mint_as reject:x; r="$CODE"
DETAIL="ok:admin $a, reject:x $r"
check "P3.wasm-realm-cannot-grant-admin-and-rejections-are-final" test "$a" = 401 -a "$r" = 401

start=$(date +%s)
mint_as loop:x; l="$CODE"
took=$(( $(date +%s) - start ))
mint_as trap:x; t="$CODE"
docker exec "$NAME" sh -c "cat $VAR/log/artifactory-service.log" > "$WORK/service.log" 2>/dev/null
timeouts="$(grep -c "echo-realm.*realms.echo.bearer.*timeout" "$WORK/service.log")"
traps="$(grep -c "echo-realm.*realms.echo.bearer.*trap" "$WORK/service.log")"
DETAIL="loop:x $l after ${took}s (deadline 2s), trap:x $t; reported timeouts $timeouts, traps $traps"
check "W4.looping-and-trapping-modules-fail-closed-and-are-reported" eval '[ "$l" = 401 ] && [ "$t" = 401 ] && [ "$took" -lt 30 ] && [ "$timeouts" -ge 1 ] && [ "$traps" -ge 1 ]'
mint_as ok:erin
DETAIL="POST $CODE as $(jq -r '.caller // empty' "$WORK/body" 2>/dev/null)"
check "W4.plugin-keeps-working-after-a-killed-step" eval '[ "$CODE" = 200 ] && [ "$(jq -r .caller "$WORK/body")" = erin ]'

http "$BASE/api/storage/wasm-generic"; anon="$CODE"
as_admin "$BASE/api/storage/wasm-generic"; adm="$CODE"
http -u "admin:wrong-password" "$BASE/api/storage/wasm-generic"; wrong="$CODE"
DETAIL="anonymous $anon, admin $adm, wrong password $wrong"
check "W6.passwords-keep-working-with-the-wasm-realm-loaded" test "$adm" = 200 -a "$wrong" = 401

# --- Removal: tokens refused, passwords still work --------------------------------------------------------------
docker exec "$NAME" rm -f "$PLUGINS_DIR/echo-realm.wasm" "$PLUGINS_DIR/echo-realm.wasm.yaml"
as_admin -X POST "$BASE/api/plugins/reload"
DETAIL="reload $CODE"
check "W1.plugin-removed-and-reloaded" test "$CODE" = 200
mint_as ok:carol; gone="$CODE"
bearer "$(token ok:frank)" "$BASE/api/storage/wasm-generic"; gone2="$CODE"
as_admin "$BASE/api/storage/wasm-generic"; adm="$CODE"
DETAIL="echoMint $gone, storage with a realm token $gone2, admin $adm"
check "W1.after-removal-realm-tokens-are-refused-and-passwords-work" eval '{ [ "$gone" = 401 ] || [ "$gone" = 404 ]; } && [ "$gone2" = 401 ] && [ "$adm" = 200 ]'

echo
say "$PASSED passed, $FAILED failed"
[ "$FAILED" = 0 ]
