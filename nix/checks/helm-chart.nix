# checks.helm-chart: deploy/helm/containercontainer lints (helm lint --strict) and renders as intended, offline.
#
#   - defaults: a single-replica StatefulSet with a var PVC at /var/opt/jfrog/artifactory, running as 1030:1030
#     non-root, probes on ping (startup, liveness) and readiness, no initial-admin environment, no Secret object;
#   - every option set: INITIAL_ADMIN_TOKEN_FILE=/run/secrets/initial-admin/token from a read-only Secret mount,
#     INITIAL_ADMIN_PASSWORD_FILE from its own read-only mount, the master key from a secretKeyRef, and
#     -Dartifactory.security.externalAdminGroups in JAVA_OPTIONS;
#   - replicas other than 1 fail the render;
#   - values.yaml sets no password, token or key value, and no render contains a Secret or a default credential.
{
  runCommand,
  kubernetes-helm,
  gnugrep,
  chart,
}:
runCommand "check-helm-chart"
  {
    nativeBuildInputs = [
      kubernetes-helm
      gnugrep
    ];
  }
  ''
    export HOME=$TMPDIR HELM_CACHE_HOME=$TMPDIR/cache HELM_CONFIG_HOME=$TMPDIR/config HELM_DATA_HOME=$TMPDIR/data
    cp -r ${chart} chart && chmod -R u+w chart
    fail() { echo "helm chart check failed: $*" >&2; exit 1; }
    has() { grep -qF -- "$2" "$1" || fail "$1 lacks: $2"; }
    lacks() { ! grep -qiE -- "$2" "$1" || fail "$1 has: $2"; }

    helm lint --strict chart

    helm template t chart > default.yaml
    has default.yaml 'kind: StatefulSet'
    has default.yaml 'replicas: 1'
    has default.yaml 'mountPath: /var/opt/jfrog/artifactory'
    has default.yaml 'storage: 20Gi'
    has default.yaml 'runAsUser: 1030'
    has default.yaml 'runAsNonRoot: true'
    has default.yaml 'path: /artifactory/api/system/ping'
    has default.yaml 'path: /artifactory/api/v1/system/readiness'
    lacks default.yaml 'INITIAL_ADMIN|JAVA_OPTIONS|secretName'

    helm template t chart \
      --set initialAdmin.tokenSecret.name=setup-token \
      --set initialAdmin.temporaryPasswordSecret.name=temp-password \
      --set masterKeySecret.name=master-key \
      --set 'security.externalAdminGroups={ops,sre}' \
      --set javaOptions=-Xmx4g > full.yaml
    has full.yaml 'value: /run/secrets/initial-admin/token'
    has full.yaml 'mountPath: /run/secrets/initial-admin'
    has full.yaml 'secretName: setup-token'
    has full.yaml 'value: /run/secrets/initial-admin-password/password'
    has full.yaml 'secretName: temp-password'
    has full.yaml 'name: master-key'
    has full.yaml 'value: "-Dartifactory.security.externalAdminGroups=ops,sre -Xmx4g"'
    [ "$(grep -c 'readOnly: true' full.yaml)" = 2 ] || fail "secret mounts are not both read-only"

    if helm template t chart --set replicas=2 > /dev/null 2> replicas.err; then fail "replicas=2 rendered"; fi
    has replicas.err 'replicas must be 1'
    if helm template t chart --set 'security.externalAdminGroups={a,b c}' > /dev/null 2> groups.err; then
      fail "a group with a space rendered"
    fi

    lacks chart/values.yaml '^[[:space:]]*[a-z]*(password|token|masterkey)[a-z]*:[[:space:]]*[^#[:space:]]'
    for f in default.yaml full.yaml; do
      lacks $f 'kind: Secret'
      lacks $f 'admin:password|password=password'
    done
    echo "helm chart: lint, default and full renders, replica and group guards, no password values: ok"
    touch $out
  ''
