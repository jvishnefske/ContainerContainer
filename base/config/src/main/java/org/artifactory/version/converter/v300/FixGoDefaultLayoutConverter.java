package org.artifactory.version.converter.v300;

import org.artifactory.version.converter.BasicFixDefaultLayoutConverter;

import static org.artifactory.util.RepoLayoutUtils.GO_DEFAULT_NAME;

/**
 * @author Uriah Levy
 */
public class FixGoDefaultLayoutConverter extends BasicFixDefaultLayoutConverter {
    private static final String FIXED_PATH_PATTERN = "[orgPath]/[module]/@v/v[baseRev](-[fileItegRev]).[ext]";

    public FixGoDefaultLayoutConverter() {
        super(GO_DEFAULT_NAME, FIXED_PATH_PATTERN);
    }
}