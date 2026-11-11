package org.artifactory.version.converter.v302;

import org.artifactory.version.converter.BasicFixDefaultLayoutConverter;

import static org.artifactory.util.RepoLayoutUtils.NPM_DEFAULT_NAME;

/**
 * @author Igor Usenko
 */
public class FixNpmDefaultLayoutConverter extends BasicFixDefaultLayoutConverter {
    private static final String FIXED_PATH_PATTERN = "[orgPath]/-/[module]-[baseRev](-[fileItegRev]).tgz";

    public FixNpmDefaultLayoutConverter() {
        super(NPM_DEFAULT_NAME, FIXED_PATH_PATTERN);
    }
}