package org.artifactory.repo.confighandler;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.ReferencedConfigChange;
import org.artifactory.system.config.change.event.SystemConfigChangeEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author shayb
 */
@Data
public class ConfigChangeEventContext {
    private final @NotNull SystemConfigChangeEvent event;
    private Map<String, RepoConfig> reposToUpdate = new HashMap<>();
    private Map<String, Set<ReferencedConfigChange.ConfigTrigger>> reposToReload = new HashMap<>();
    private Map<AbstractConfigDescriptorEventHandler, Map<ConfigChangeType, List<String>>> descriptorChanges;

    public ConfigChangeEventContext(SystemConfigChangeEvent event) {
        this.event = event;
    }
}
