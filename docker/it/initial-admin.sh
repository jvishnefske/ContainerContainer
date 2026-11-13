# Sourced by the docker/it scripts: claims a freshly started server (design IA1-IA8) so the tests get administrator
# credentials without any default password. Not executable on its own.
#
# The server writes a one-time setup token to var/etc/security/initial-admin.token on first start and answers 503
# to everything but ping, the probes and the claim until POST /api/setup/initial-admin {"token","password"} succeeds.
# ia_claim_server reads that token from the container, claims with a random password generated here, and sets
# ADMIN_USER, ADMIN_PASSWORD and ADMIN (user:password). The password lives only in this shell and in the caller's
# private work directory (the claim body is sent from a mode 0600 file, never on a command line).
#
# Needs: docker, curl, openssl; the caller's WORK (a private temporary directory).

IA_TOKEN_PATH=/var/opt/jfrog/artifactory/etc/security/initial-admin.token
IA_TOKEN_TIMEOUT=120

# ia_password: a random password that passes the password policy (at least 12 characters, not a common one).
ia_password() { printf 'It-%s-Pw' "$(openssl rand -hex 16)"; }

# ia_read_token <container>: prints the setup token the server wrote, waiting up to IA_TOKEN_TIMEOUT seconds.
ia_read_token() {
    local waited=0 token
    while [ "$waited" -lt "$IA_TOKEN_TIMEOUT" ]; do
        token="$(docker exec "$1" cat "$IA_TOKEN_PATH" 2>/dev/null)" && [ -n "$token" ] && {
            printf '%s' "$token"; return 0
        }
        sleep 2; waited=$((waited + 2))
    done
    return 1
}

# ia_token_present <container>: succeeds when the token file exists.
ia_token_present() { docker exec "$1" test -e "$IA_TOKEN_PATH"; }

# ia_claim <base> <token> <password>: posts the claim; prints the HTTP status code (000 when unreachable).
ia_claim() {
    local body="$WORK/ia-claim.json" code
    (umask 077 && jq -n --arg t "$2" --arg p "$3" '{token: $t, password: $p}' > "$body")
    code="$(curl -s -m 60 -o "$WORK/ia-claim.out" -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
        --data-binary "@$body" "$1/api/setup/initial-admin")" || code=000
    rm -f "$body"
    printf '%s' "$code"
}

# ia_claim_server <container> <base>: claims the server as admin with a new random password and sets ADMIN_USER,
# ADMIN_PASSWORD and ADMIN. Fails (status 1, message on stderr) when the token cannot be read or the claim fails.
ia_claim_server() {
    local token code
    token="$(ia_read_token "$1")" || { echo "initial-admin: no setup token in $1:$IA_TOKEN_PATH" >&2; return 1; }
    ADMIN_USER=admin
    ADMIN_PASSWORD="$(ia_password)"
    code="$(ia_claim "$2" "$token" "$ADMIN_PASSWORD")"
    [ "$code" = 200 ] || { echo "initial-admin: claim answered $code" >&2; return 1; }
    ADMIN="$ADMIN_USER:$ADMIN_PASSWORD"
}
