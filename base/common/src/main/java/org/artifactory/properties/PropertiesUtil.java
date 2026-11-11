package org.artifactory.properties;

import lombok.experimental.UtilityClass;
import org.artifactory.api.properties.PropertiesDiff;
import org.artifactory.md.Properties;

import java.util.Collection;
import java.util.Set;

/**
 * @author Tamar Rosen
 */
@UtilityClass
public class PropertiesUtil {
    public static PropertiesDiff calcPropertiesDiff(Properties updatedProperties, Properties currentProperties) {
        Collection<String> currentKeys = currentProperties.keySet();
        Properties modified = new PropertiesImpl(updatedProperties);
        Properties deleted = new PropertiesImpl();
        Collection<String> modifiedKeys = modified.keySet();
        for (String key : currentKeys) {
            Set<String> values = currentProperties.get(key);
            if (modifiedKeys.contains(key)) {
                Set<String> other = modified.get(key);
                if (values != null && values.equals(other)) {
                    modified.removeAll(key);
                }
            } else {
                deleted.put(key, "");
            }
        }
        return new PropertiesDiff(modified, deleted);
    }
}
