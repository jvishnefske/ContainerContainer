package org.artifactory.api.rest.constant;

import lombok.experimental.UtilityClass;

/**
 * A fake package type to use for internal/system REST APIs that do not belong to any specific package type,
 * like system usage logs, Federation full sync files, etc.
 *
 * @author Sergey Steinvil
 */
@UtilityClass
public class FakeSystemPackageType {
    // System usage logs, log shipping, etc.
    public final String LOGS = "_logs";
    // Federation full sync files
    public final String FEDERATION = "_federation";
    // Grid orchestration APIs (repo sync, snapshots, etc.)
    public final String GRID = "_grid";
}
