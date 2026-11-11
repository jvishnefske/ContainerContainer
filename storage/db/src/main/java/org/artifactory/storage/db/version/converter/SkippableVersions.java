package org.artifactory.storage.db.version.converter;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.artifactory.common.ConstantValues;
import org.jfrog.converter.DbVersion;

import java.util.List;
import java.util.Map;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v120;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v121;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v122;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v123;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v124;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v125;
import static org.artifactory.storage.db.version.converter.SkippableVersions.SkippableConverters.NODE_PROPS_VARCHAR_PATTERN_OPS;

/**
 * @author Tomer Slutsky
 */
public class SkippableVersions {
    private static final Map<String, List<DbVersion>> converters = Map.of(
            NODE_PROPS_VARCHAR_PATTERN_OPS.getName(), List.of(v120, v121, v122, v123, v124, v125));

    public static boolean shouldSkipConverter(DbVersion version) {
        for (String converter : ConstantValues.skipSkippableDbConverters.getList()) {
            List<DbVersion> versions = converters.getOrDefault(converter.toLowerCase(), List.of());
            if (versions.contains(version)) {
                return true;
            }
        }

        return false;
    }

    @AllArgsConstructor
    @Getter
    public enum SkippableConverters {
        NODE_PROPS_VARCHAR_PATTERN_OPS("node_props_varchar_pattern_ops");
        private final String name;
    }
}
