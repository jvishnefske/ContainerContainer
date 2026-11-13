#!@shell@
# Entrypoint of the minimal ContainerContainer image (nix/image.nix): prepares the var directory and runs
# Artifactory's Tomcat as PID 1 (SIGTERM stops it cleanly). POSIX sh on busybox; store paths are filled in at
# build time.
#
# Access runs embedded in the webapp (the default, artifactory.access.embedded.enabled=true): users, groups,
# permissions and tokens live in Artifactory's own database (Derby or PostgreSQL), so the image needs no Access
# service, join key or router. The launcher sets no Access option; the environment is passed to the JVM unchanged.
#
# Environment:
#   JF_SHARED_SECURITY_MASTERKEY
#                   written to var/etc/security/master.key on first start when that file is missing (otherwise a
#                   random key). The embedded token signing key is encrypted with it, so the file must persist
#                   with the var volume; a changed master key makes startup refuse to continue.
#   INITIAL_ADMIN_TOKEN_FILE
#                   read by Artifactory, not by the launcher: a file holding the one-time setup token (at least 22
#                   characters) used to claim the server. Without it Artifactory writes a random token to
#                   var/etc/security/initial-admin.token on first start.
#   INITIAL_ADMIN_PASSWORD_FILE
#                   read by Artifactory: a file with a temporary administrator password; the server starts claimed
#                   and the administrator must change that password at first login.
#   JF_SHARED_NODE_ID, JF_SHARED_NODE_IP
#                   node id and address written to a new system.yaml (defaults: host name, 127.0.0.1)
#   JAVA_OPTIONS    extra JVM options, appended last so they override the defaults (-Xms512m -Xmx2g); Artifactory
#                   system properties go here, for example -Dartifactory.security.externalAdminGroups=ops
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
    -Djava.util.logging.config.file="$base/conf/logging.properties" \
    -Djava.util.logging.manager=org.apache.juli.ClassLoaderLogManager \
    -Dcatalina.base="$base" -Dcatalina.home="$tomcat" -Djava.io.tmpdir="$temp" \
    ${JAVA_OPTIONS:-} \
    -classpath "$tomcat/bin/bootstrap.jar:$tomcat/bin/tomcat-juli.jar" \
    org.apache.catalina.startup.Bootstrap start
