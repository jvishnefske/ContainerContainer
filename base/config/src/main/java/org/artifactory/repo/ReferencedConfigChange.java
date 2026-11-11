package org.artifactory.repo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Set;


/**
 * Class represent a repository CREATE/UPDATE/DELETE operation, it can be used before/after save operations and to
 * reload a state in the system after a change was done
 *
 * @author shayb
 */
@Data
@AllArgsConstructor
public class ReferencedConfigChange {
    private String repoKey;
    private Set<ConfigTrigger> configTriggers;

    public enum ConfigTrigger {
        REFERENCED_URL_BASE_CHANGE,
        REFERENCED_PROXY_CHANGE,
        REFERENCED_LAYOUT_CHANGE,
        REFERENCED_PROPERTY_SET_CHANGE,
        REFERENCED_KEYPAIR_CHANGE,
        REFERENCED_KEYSTORE_CHANGE
    }
}
