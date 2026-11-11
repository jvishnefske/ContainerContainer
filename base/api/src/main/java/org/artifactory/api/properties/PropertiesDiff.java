package org.artifactory.api.properties;

import lombok.Data;
import org.artifactory.md.Properties;

/**
 * @author Tamar Rosen
 */
@Data
public class PropertiesDiff {
    private final Properties modified;
    private final Properties deleted;
}
