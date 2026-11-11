package org.artifactory.repo.confighandler.manager;

import jakarta.annotation.Nonnull;
import lombok.Getter;
import org.artifactory.repo.confighandler.AbstractConfigDescriptorEventHandler;
import org.artifactory.repo.confighandler.ConfigChangeType;
import org.artifactory.system.config.change.event.SystemConfigChangeEvent;
import org.artifactory.workqueue.WorkItem;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author Dor Solomon
 *
 * Config descriptor change work item should be created only by {@link ConfigChangeEventHandlerManager} as an output of
 * partial config descriptor change handling. The work item contains the analyzed config descriptor changes that have
 * found but haven't been handled.
 * @see ConfigChangeEventHandlerManager#handleOnlyConfigDescriptorDeleteChange
 */
@Getter
public class ConfigDescriptorChangeWorkItem extends WorkItem {

    private final SystemConfigChangeEvent configChangeEvent;
    private final Map<AbstractConfigDescriptorEventHandler, Map<ConfigChangeType, List<String>>> descriptorChanges;

    protected ConfigDescriptorChangeWorkItem(SystemConfigChangeEvent configChangeEvent,
            Map<AbstractConfigDescriptorEventHandler, Map<ConfigChangeType, List<String>>> descriptorChanges) {
        this.configChangeEvent = configChangeEvent;
        this.descriptorChanges = descriptorChanges;
    }

    @Nonnull
    @Override
    public String getUniqueKey() {
        return String.valueOf(hashCode());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {return true;}
        if (o == null || getClass() != o.getClass()) {return false;}
        ConfigDescriptorChangeWorkItem that = (ConfigDescriptorChangeWorkItem) o;
        return descriptorChanges.equals(that.descriptorChanges);
    }

    @Override
    public int hashCode() {
        return Objects.hash(descriptorChanges);
    }
}
