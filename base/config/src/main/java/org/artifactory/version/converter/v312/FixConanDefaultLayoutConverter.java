package org.artifactory.version.converter.v312;

import org.artifactory.version.converter.BasicFixDefaultLayoutConverter;

import static org.artifactory.util.RepoLayoutUtils.CONAN_DEFAULT_NAME;

/**
 * @author volodymyrm
 */
public class FixConanDefaultLayoutConverter extends BasicFixDefaultLayoutConverter {
    private static final String FIXED_PATH_PATTERN =
            "[org]/[module]/[baseRev]/[channel<[^/]+>]/[folderItegRev]/(package/[package_id<[^/]+>]/[fileItegRev]/)" +
                    "[remainder<(?:.+)>]";

    public FixConanDefaultLayoutConverter() {
        super(CONAN_DEFAULT_NAME, FIXED_PATH_PATTERN);
    }
}