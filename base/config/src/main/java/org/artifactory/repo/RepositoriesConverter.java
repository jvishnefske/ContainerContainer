package org.artifactory.repo;

import java.util.Map;

/**
 * @author Tamar Rosen
 */
public interface RepositoriesConverter {
    boolean isInterested(long version);

    void convert(Map<String, Object> toConvert);
}
