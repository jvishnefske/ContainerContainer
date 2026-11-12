# Overlay image: the stock Artifactory OSS 7.161.26 image with the fork's jars swapped into its exploded webapp.
#
# Build with docker/build-overlay.sh, which stages the build context:
#   overlay/lib/      the jars to install (fork-built artifactory-*.jar plus new runtime dependencies)
#   overlay/replace   names of stock jars that must already exist and are overwritten
#   overlay/add       names of jars that must not exist yet and are added
#   overlay/stock     the stock WEB-INF/lib listing the context was staged against (LC_ALL=C sorted)
#   overlay/expected  the listing WEB-INF/lib must have afterwards: stock plus the added jars (LC_ALL=C sorted)
#
# The build fails if the stock listing differs from what the script saw, if a jar to replace is missing, if a
# jar to add is already present, or if the result is not exactly stock plus the added jars. Nothing outside
# WEB-INF/lib is changed. The classic builder (no BuildKit) is assumed, so the context is COPYed to /overlay and
# deleted after installing; that COPY layer remains in the image history.
ARG BASE_IMAGE=releases-docker.jfrog.io/jfrog/artifactory-oss:7.161.26
FROM ${BASE_IMAGE}

ARG WEBAPP_LIB=/opt/jfrog/artifactory/app/artifactory/tomcat/webapps/artifactory/WEB-INF/lib

USER root
COPY overlay/ /overlay/
RUN set -eu; \
    lib="${WEBAPP_LIB}"; \
    ls "$lib" | LC_ALL=C sort > /tmp/stock.now; \
    if ! cmp -s /tmp/stock.now /overlay/stock; then \
        echo "FATAL: stock WEB-INF/lib differs from the listing the overlay was staged against" >&2; \
        diff /overlay/stock /tmp/stock.now >&2 || true; exit 1; \
    fi; \
    while read -r jar; do \
        [ -n "$jar" ] || continue; \
        [ -f "$lib/$jar" ] || { echo "FATAL: stock jar to replace is missing: $jar" >&2; exit 1; }; \
        [ -f "/overlay/lib/$jar" ] || { echo "FATAL: overlay jar missing from context: $jar" >&2; exit 1; }; \
    done < /overlay/replace; \
    while read -r jar; do \
        [ -n "$jar" ] || continue; \
        [ ! -e "$lib/$jar" ] || { echo "FATAL: jar to add already exists: $jar" >&2; exit 1; }; \
        [ -f "/overlay/lib/$jar" ] || { echo "FATAL: overlay jar missing from context: $jar" >&2; exit 1; }; \
    done < /overlay/add; \
    cat /overlay/replace /overlay/add | while read -r jar; do \
        [ -n "$jar" ] || continue; \
        cp "/overlay/lib/$jar" "$lib/$jar"; \
        chown artifactory:artifactory "$lib/$jar"; \
        chmod 0644 "$lib/$jar"; \
    done; \
    ls "$lib" | LC_ALL=C sort > /tmp/lib.after; \
    if ! cmp -s /tmp/lib.after /overlay/expected; then \
        echo "FATAL: resulting WEB-INF/lib is not stock plus the added jars" >&2; \
        diff /overlay/expected /tmp/lib.after >&2 || true; exit 1; \
    fi; \
    rm -rf /overlay /tmp/stock.now /tmp/lib.after
USER artifactory

LABEL org.opencontainers.image.title="artifactory-oss-fork" \
      org.opencontainers.image.description="Artifactory OSS 7.161.26 with the fork's Groovy plugin engine and plugin package types" \
      org.opencontainers.image.licenses="AGPL-3.0-or-later"
