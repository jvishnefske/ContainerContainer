package org.artifactory.repo.converter;

import java.util.Map;

/**
 * @author Tamar Rosen
 */
public interface RepositoriesConfigSubConverter {

    boolean isInterested(String key);

    void convert(Map<String, Object> toConvert);
}
