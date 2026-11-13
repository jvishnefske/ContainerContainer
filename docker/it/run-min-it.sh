#!/usr/bin/env bash
# Live integration test of the minimal from-scratch OCI image (design.md N3, N6, IA1-IA8): packages.oci-example,
# which is lib.mkImage with the public example plugins (plugins/oci/oci-registry.groovy and the echo-realm WASM
# module). The image runs alone: access is embedded, so there is no Access service, sidecar, join key or network.
#
#  1. Builds packages.oci-example with Nix and loads it with `docker load` (SKIP_BUILD=1 reuses a loaded image;
#     IMAGE_TAR loads a given archive instead).
#  2. Starts rt-min-it on a fresh volume, publishing only port 8081 on 127.0.0.1.
#  3. Before the claim (IA1, IA2, IA4, IA8): ping and the probes answer; admin:password, an empty password, anonymous
#     requests and arbitrary endpoints (REST, token mint, repository content, /v2/) all answer 503 with the fixed
#     body; the setup token file exists, is private (0600) and long enough.
#  4. The claim (IA3, IA5): a wrong token and a weak password are refused with 400 and do not use up the token; the
#     right token with a random strong password (generated here, kept only in this shell and the private work
#     directory) answers 200; the token file is gone; a second claim with the same token answers 400;
#     admin:password and an empty password answer 401 and the new password logs in.
#  5. Every functional check, one PASS/FAIL line each, as the claimed administrator: the image runs as a non-root
#     user; a YAML configuration PATCH creates a Generic repository; deploy and download round trip with matching
#     checksums; an access token minted through Artifactory works as a Bearer token and a tampered one does not;
#     the oci-registry plugin is loaded, /v2/ answers 401 to anonymous and 200 to admin, and a blob and manifest
#     push and pull round trip through /v2/; the echo-realm WASM plugin is loaded and a realm Bearer token runs
#     its echoMint execution as the realm user; no web UI is served.
#  6. Restart (IA6, embedded access persistence): after `docker restart` the server is still claimed (no 503, the
#     old setup token is refused, the token file is still gone, no new one is written), the administrator
#     password still works, and the access token minted before the restart still verifies.
#
# Clients talk only to 127.0.0.1:<port> of the container this script starts. No address range is probed.
#
# The container is named rt-min-it and the volume rt-min-it-var; leftovers with those names are removed first, and
# everything is removed again on exit.
#
# Usage: docker/it/run-min-it.sh   (env: SKIP_BUILD=1, IMAGE, IMAGE_TAR, LOG_DIR keeps container logs, TMPDIR)
# Exit status: 0 when every assertion passes, 1 otherwise.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
VERSION="$(cat "$ROOT/nix/version.txt")"
IMAGE="${IMAGE:-containercontainer:$VERSION-example}"
NAME=rt-min-it
VOLUME=rt-min-it-var
VAR=/var/opt/jfrog/artifactory
ISSUER=echo-it
STARTUP_TIMEOUT=600
LOG_DIR="${LOG_DIR:-$(mktemp -d "${TMPDIR:-/tmp}/rt-min-it-logs.XXXXXX")}"
WORK="$(mktemp -d "${TMPDIR:-/tmp}/rt-min-it-work.XXXXXX")"
chmod 0700 "$WORK"
mkdir -p "$LOG_DIR"
# shellcheck source=initial-admin.sh
. "$ROOT/docker/it/initial-admin.sh"
ADMIN=""

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
    docker rm -f "$NAME" >/dev/null 2>&1 || true
    docker volume rm "$VOLUME" >/dev/null 2>&1 || true
}
cleanup() {
    local rc=$?
    docker container inspect "$NAME" >/dev/null 2>&1 && docker logs "$NAME" > "$LOG_DIR/$NAME.log" 2>&1
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
code_of() { http "$@"; printf '%s' "$CODE"; }
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
say "minimal image $(docker image inspect "$IMAGE" --format '{{.Id}}')"

docker volume create "$VOLUME" >/dev/null
docker run -d --name "$NAME" -p "127.0.0.1:$PORT:8081" -v "$VOLUME:$VAR" "$IMAGE" >/dev/null
wait_for_ping || { DETAIL="see $LOG_DIR"; check "setup.artifactory-starts" false; exit 1; }
DETAIL="GET /api/system/ping OK before the claim"
check "N3.ping" true

# --- Image basics -------------------------------------------------------------------------------------------------
uid="$(docker exec "$NAME" id -u)"
user="$(docker image inspect "$IMAGE" --format '{{.Config.User}}')"
env_access="$(docker image inspect "$IMAGE" --format '{{range .Config.Env}}{{println .}}{{end}}' | grep -c '^ACCESS_')"
DETAIL="process uid $uid, image user $user, ACCESS_* variables in the image: $env_access"
check "N6.runs-as-non-root-without-access-config" eval '[ "$uid" = 1030 ] && [ "$user" = 1030:1030 ] && [ "$env_access" = 0 ]'

# --- Before the claim: closed, no default credential (IA1, IA2, IA4, IA8) ------------------------------------------
TOKEN_FILE_INFO="$(docker exec "$NAME" stat -c '%a %s' "$IA_TOKEN_PATH" 2>/dev/null)"
SETUP_TOKEN="$(ia_read_token "$NAME")"
DETAIL="token file mode/size: ${TOKEN_FILE_INFO:-missing}, token ${#SETUP_TOKEN} chars"
check "IA2.setup-token-file-written-private" eval '[ "${TOKEN_FILE_INFO%% *}" = 600 ] && [ "${#SETUP_TOKEN}" -ge 22 ]'

ready="$(code_of "$BASE/api/v1/system/readiness")"
live="$(code_of "$BASE/api/v1/system/liveness")"
DETAIL="readiness $ready, liveness $live"
check "IA8.probes-answer-before-claim" eval '[ "$ready" = 200 ] && [ "$live" = 200 ]'

default_pw="$(code_of -u admin:password "$BASE/api/system/version")"
blocked_body="$(cat "$WORK/body")"
empty_pw="$(code_of -u admin: "$BASE/api/system/version")"
anon="$(code_of "$BASE/api/repositories")"
DETAIL="admin:password $default_pw ('$blocked_body'), admin with empty password $empty_pw, anonymous $anon"
check "IA1.IA8.default-and-empty-password-503-before-claim" eval '[ "$default_pw" = 503 ] && [ "$empty_pw" = 503 ] && [ "$anon" = 503 ] && [ "$blocked_body" = "initial setup required" ]'

blocked=""
for req in "GET $BASE/api/system/configuration" "GET $BASE/api/security/users" "POST $BASE/api/security/token" \
           "GET $BASE/api/plugins" "PUT $BASE/min-generic/a.txt" "GET $BASE/api/storage/min-generic" \
           "GET $REG/v2/" "GET $BASE/webapp/" "GET $BASE/api/system/ping/../version" \
           "POST $BASE/api/setup/initial-admin/x"; do
    blocked="$blocked $(code_of -X "${req%% *}" -u admin:password "${req#* }")"
done
DETAIL="arbitrary endpoints:$blocked"
check "IA8.arbitrary-endpoints-503-before-claim" eval '[ "$(tr " " "\n" <<< "$blocked" | grep -v "^$" | sort -u)" = 503 ]'

# --- The claim (IA3, IA5) --------------------------------------------------------------------------------------------
ADMIN_USER=admin
ADMIN_PASSWORD="$(ia_password)"
wrong="$(ia_claim "$BASE" "$(openssl rand -base64 32 | tr '+/' '-_' | tr -d '=')" "$ADMIN_PASSWORD")"
wrong_body="$(cat "$WORK/ia-claim.out")"
weak="$(ia_claim "$BASE" "$SETUP_TOKEN" password)"
weak_body="$(cat "$WORK/ia-claim.out")"
DETAIL="wrong token $wrong ($wrong_body), right token with weak password $weak ($weak_body)"
check "IA3.wrong-token-and-weak-password-400" eval '[ "$wrong" = 400 ] && [ "$weak" = 400 ] && [ "$wrong_body" = "$weak_body" ]'

claim="$(ia_claim "$BASE" "$SETUP_TOKEN" "$ADMIN_PASSWORD")"
claim_body="$(cat "$WORK/ia-claim.out")"
ADMIN="$ADMIN_USER:$ADMIN_PASSWORD"
DETAIL="claim $claim ($claim_body)"
check "IA3.claim-with-setup-token-200" eval '[ "$claim" = 200 ]'

if ia_token_present "$NAME"; then gone=present; else gone=gone; fi
DETAIL="token file after claim: $gone"
check "IA5.token-file-deleted-after-claim" test "$gone" = gone

second="$(ia_claim "$BASE" "$SETUP_TOKEN" "$(ia_password)")"
DETAIL="second claim with the same token $second ($(cat "$WORK/ia-claim.out"))"
check "IA5.second-claim-400" test "$second" = 400

as_admin "$BASE/api/system/version"; v="$CODE"; version="$(jq -r '.version // empty' "$WORK/body" 2>/dev/null)"
default_pw="$(code_of -u admin:password "$BASE/api/system/version")"
empty_pw="$(code_of -u admin: "$BASE/api/system/version")"
wrong_pw="$(code_of -u admin:wrong-password "$BASE/api/system/version")"
DETAIL="claimed password $v (version $version), admin:password $default_pw, empty password $empty_pw, wrong password $wrong_pw"
check "IA1.N3.admin-login-after-claim-default-password-401" eval '[ "$v" = 200 ] && [ "$default_pw" = 401 ] && [ "$empty_pw" = 401 ] && [ "$wrong_pw" = 401 ]'

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
as_admin -X POST -d username=admin -d scope=member-of-groups:readers -d expires_in=3600 "$BASE/api/security/token"
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
DETAIL="echo-realm:$(jq -c '.plugins["echo-realm"] // empty' "$WORK/plugins.json" 2>/dev/null)"
check "N3.echo-realm-wasm-plugin-loaded" eval 'jq -e ".plugins[\"echo-realm\"].status == \"Loaded\" and .plugins[\"echo-realm\"].realms == \"echo\"" "$WORK/plugins.json" >/dev/null'
http -H "Authorization: Bearer $(realm_token ok:carol)" -X POST "$BASE/api/plugins/execute/echoMint"
caller="$(jq -r '.caller // empty' "$WORK/body" 2>/dev/null)"; minted="$(jq -r '.access_token // empty' "$WORK/body" 2>/dev/null)"
c="$CODE"
# Embedded access seeds no groups or permission targets (stock Access seeds a readers group and the Anything
# permission target, and OSS has no API to create groups), so the realm user gets read on min-generic directly,
# through the UI REST API (the public security API is a Pro feature in OSS), once its login has created it.
as_admin -X POST -H 'Content-Type: application/json' -H 'X-Requested-With: XMLHttpRequest' \
    -d '{"name":"min-it-carol","repoPermission":{"repoKeys":["min-generic"],"includePatterns":["**"],"excludePatterns":[],"userPermissionActions":[{"principal":"carol","actions":["read"]}],"groupPermissionActions":[]}}' \
    "$BASE/ui/permissiontargets"; perm="$CODE"
http -H "Authorization: Bearer $minted" "$BASE/api/storage/min-generic"; used="$CODE"
http -H "Authorization: Bearer $(realm_token reject:x)" -X POST "$BASE/api/plugins/execute/echoMint"; rejected="$CODE"
DETAIL="echoMint $c as '$caller', read permission for carol $perm, minted token use $used, rejected token $rejected"
check "N3.echo-realm-bearer-login-runs-execution" eval '[ "$c" = 200 ] && [ "$caller" = carol ] && [ -n "$minted" ] && [ "$perm" = 201 ] && [ "$used" = 200 ] && [ "$rejected" = 401 ]'

# --- No web UI ------------------------------------------------------------------------------------------------------
http "$BASE/webapp/"; webapp="$CODE"
http "$REG/ui/"; ui="$CODE"
DETAIL="/artifactory/webapp/ $webapp, /ui/ $ui"
check "N6.no-web-ui" eval '[ "$webapp" != 200 ] && [ "$ui" != 200 ]'

# --- Restart: still claimed, tokens still verify (IA6) --------------------------------------------------------------
say "restarting $NAME"
docker restart -t 120 "$NAME" >/dev/null
if wait_for_ping; then
    anon="$(code_of "$BASE/api/repositories")"
    adm="$(code_of -u "$ADMIN" "$BASE/api/system/version")"
    default_pw="$(code_of -u admin:password "$BASE/api/system/version")"
    again="$(ia_claim "$BASE" "$SETUP_TOKEN" "$(ia_password)")"
    if ia_token_present "$NAME"; then gone=present; else gone=gone; fi
    DETAIL="anonymous $anon, admin $adm, admin:password $default_pw, claim with the old token $again, token file $gone"
    check "IA6.still-claimed-after-restart" eval '[ "$anon" != 503 ] && [ "$adm" = 200 ] && [ "$default_pw" = 401 ] && [ "$again" = 400 ] && [ "$gone" = gone ]'
    old="$(code_of -H "Authorization: Bearer $TOKEN" "$BASE/api/repositories")"
    tampered="$(code_of -H "Authorization: Bearer ${TOKEN}x" "$BASE/api/repositories/min-generic")"
    as_admin "$BASE/min-generic/a/file.bin"; get="$CODE"
    same="$(cmp -s "$WORK/file.bin" "$WORK/body" && echo identical || echo different)"
    DETAIL="token minted before restart $old, tampered $tampered, artifact GET $get ($same)"
    check "IA6.old-token-verifies-after-restart" eval '[ "$old" = 200 ] && [ "$tampered" = 401 ] && [ "$get" = 200 ] && [ "$same" = identical ]'
else
    DETAIL="see $LOG_DIR"; check "IA6.restart" false
fi

echo
say "$PASSED passed, $FAILED failed"
[ "$FAILED" = 0 ]
