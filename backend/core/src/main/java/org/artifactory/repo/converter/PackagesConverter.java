package org.artifactory.repo.converter;

import java.util.Map;

/**
 * @author Tamar Rosen
 */
public interface PackagesConverter {
    boolean isInterested(long version);

    void convert(Map<String, String> toConvert);
}
