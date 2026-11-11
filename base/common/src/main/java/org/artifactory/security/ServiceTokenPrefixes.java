package org.artifactory.security;

import lombok.experimental.UtilityClass;

import java.util.Map;

/**
 * Maps JFrog service token prefixes to their logical service names.
 * Token subjects follow the format {@code prefix@instanceId} (for example {@code jfxr@01abc...}).
 */
@UtilityClass
public final class ServiceTokenPrefixes {

    public static final Map<String, String> TOKEN_PREFIX_TO_SERVICE = Map.ofEntries(
            Map.entry("jfac", "access"),
            Map.entry("jfrt", "artifactory"),
            Map.entry("jfrtfs", "artifactory-federation"),
            Map.entry("jfrou", "router"),
            Map.entry("jfmc", "mission-control"),
            Map.entry("jfxr", "xray-service"),
            Map.entry("jfxidx", "xray-indexer"),
            Map.entry("jfxpst", "xray-persist"),
            Map.entry("jfxana", "xray-analysis"),
            Map.entry("jfds", "distribution"),
            Map.entry("jffe", "frontend"),
            Map.entry("jfevt", "event"),
            Map.entry("jfpip", "pipelines"),
            Map.entry("jfxfer", "replicator"),
            Map.entry("jfcon", "jf-connect"),
            Map.entry("jflnk", "jf-link"),
            Map.entry("jfob", "observability"),
            Map.entry("jfint", "integration"),
            Map.entry("jftrk", "tracker"),
            Map.entry("jfisv", "insight"),
            Map.entry("jfmd", "metadata"),
            Map.entry("jfwks", "worker"),
            Map.entry("jfsupport", "internal-support"),
            Map.entry("jfevd", "evidence"),
            Map.entry("jfbus", "jfbus"),
            Map.entry("jfapp", "apptrust"),
            Map.entry("jfcatalog", "catalog")
    );
}
