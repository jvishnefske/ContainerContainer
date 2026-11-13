#!@shell@
# Entrypoint of the minimal ContainerContainer image (nix/image.nix): prepares the var directory and runs
# Artifactory's Tomcat as PID 1 (SIGTERM stops it cleanly). POSIX sh on busybox; store paths are filled in at
# build time.
#
# Environment:
#   ACCESS_URL      HTTP base URL of the Access-compatible service (default http://localhost:8040/access)
#   ACCESS_GRPC     host:port of its gRPC endpoint (default localhost:8045); the defaults suit a sidecar in the
#                   same network namespace
#   JF_SHARED_SECURITY_JOINKEY, JF_SHARED_SECURITY_MASTERKEY
#                   written to var/etc/security/{join,master}.key on first start when those files are missing
#                   (otherwise random keys); the join key must be the one the Access service knows
#   JF_SHARED_NODE_ID, JF_SHARED_NODE_IP
#                   node id and address written to a new system.yaml (defaults: host name, 127.0.0.1)
#   JAVA_OPTIONS    extra JVM options, appended last so they override the defaults (-Xms512m -Xmx2g)
#
# On every start the plugins built into the image (/opt/jfrog/artifactory/plugins, read-only) are copied into
# var/etc/artifactory/plugins, replacing files of the same name, so an image upgrade updates them even when var is
# a volume. Plugins added to that directory by other means are left alone.
set -eu

home=/opt/jfrog/artifactory
var=/var/opt/jfrog/artifactory
etc=$var/etc
temp=$var/work/artifactory/tomcat/temp

mkdir -p "$etc/security" "$etc/artifactory/plugins" "$temp" "$var/log" "$var/data"

random_key() { od -An -tx1 -N32 /dev/urandom | tr -d ' \n'; }
write_key() { # write_key <file> <value or empty>
    [ -s "$1" ] && return 0
    (umask 077 && printf '%s' "${2:-$(random_key)}" > "$1")
}
write_key "$etc/security/master.key" "${JF_SHARED_SECURITY_MASTERKEY:-}"
write_key "$etc/security/join.key" "${JF_SHARED_SECURITY_JOINKEY:-}"

if [ ! -e "$etc/system.yaml" ]; then
    cat > "$etc/system.yaml" <<EOF
# Written by the image's launcher on first start; edit freely, it is not rewritten.
configVersion: 1
shared:
  node:
    id: ${JF_SHARED_NODE_ID:-$(hostname)}
    ip: ${JF_SHARED_NODE_IP:-127.0.0.1}
  database:
    # The embedded Derby database; for production configure PostgreSQL (the driver is in the image).
    allowNonPostgresql: true
router:
  # This image has no JFrog router; Tomcat serves port 8081 directly.
  enabled: false
EOF
fi

for plugin in "$home"/plugins/*; do
    [ -f "$plugin" ] || continue
    name=$(basename "$plugin")
    rm -f "$etc/artifactory/plugins/$name"
    cp "$plugin" "$etc/artifactory/plugins/$name"
    chmod 0644 "$etc/artifactory/plugins/$name"
done

base=@base@
tomcat=@tomcat@
# shellcheck disable=SC2086 # JAVA_OPTIONS is a list of options
exec @java@ \
    -Xms512m -Xmx2g -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError -XX:+EnableDynamicAgentLoading \
    --add-opens=java.base/java.lang=ALL-UNNAMED \
    --add-opens=java.base/java.lang.reflect=ALL-UNNAMED \
    --add-opens=java.base/java.lang.invoke=ALL-UNNAMED \
    --add-opens=java.base/java.io=ALL-UNNAMED \
    --add-opens=java.base/java.util=ALL-UNNAMED \
    --add-opens=java.base/java.util.concurrent=ALL-UNNAMED \
    --add-opens=java.base/java.text=ALL-UNNAMED \
    --add-opens=java.base/java.nio=ALL-UNNAMED \
    --add-opens=java.rmi/sun.rmi.transport=ALL-UNNAMED \
    --add-opens=java.desktop/java.awt.font=ALL-UNNAMED \
    --add-opens=java.naming/com.sun.jndi.ldap=ALL-UNNAMED \
    --enable-native-access=ALL-UNNAMED \
    -Djava.awt.headless=true \
    -Dfile.encoding=UTF8 \
    -Djava.security.egd=file:/dev/./urandom \
    -Djdk.tls.ephemeralDHKeySize=2048 \
    -Djruby.compile.invokedynamic=false -Djruby.bytecode.version=1.8 \
    -Dartdist=docker \
    -Djf.product.home="$home" \
    -Dartifactory.access.client.serverUrl.override="${ACCESS_URL:-http://localhost:8040/access}" \
    -Dartifactory.access.client.grpcUrl.override="${ACCESS_GRPC:-localhost:8045}" \
    -Djava.util.logging.config.file="$base/conf/logging.properties" \
    -Djava.util.logging.manager=org.apache.juli.ClassLoaderLogManager \
    -Dcatalina.base="$base" -Dcatalina.home="$tomcat" -Djava.io.tmpdir="$temp" \
    ${JAVA_OPTIONS:-} \
    -classpath "$tomcat/bin/bootstrap.jar:$tomcat/bin/tomcat-juli.jar" \
    org.apache.catalina.startup.Bootstrap start
