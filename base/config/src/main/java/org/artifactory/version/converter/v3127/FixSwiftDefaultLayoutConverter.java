package org.artifactory.version.converter.v3127;

import org.artifactory.version.converter.BasicFixDefaultLayoutConverter;

import static org.artifactory.util.RepoLayoutUtils.SWIFT_DEFAULT_NAME;

/**
 * @author Asaf Zalcman
 */
public class FixSwiftDefaultLayoutConverter extends BasicFixDefaultLayoutConverter {
    public FixSwiftDefaultLayoutConverter() {
        super(SWIFT_DEFAULT_NAME, "[scope]/[name]/[name]-[version].zip");
    }
}