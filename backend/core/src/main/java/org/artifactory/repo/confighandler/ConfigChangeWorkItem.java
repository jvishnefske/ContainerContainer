package org.artifactory.repo.confighandler;

import jakarta.annotation.Nonnull;
import lombok.Data;
import org.artifactory.system.config.change.event.SystemConfigChangeEvent;
import org.artifactory.workqueue.WorkItem;

import java.util.Objects;

/**
 * @author Mor Merhav
 */
@Data
public class ConfigChangeWorkItem extends WorkItem {

    private final SystemConfigChangeEvent configChangeEvent;

    @Nonnull
    @Override
    public String getUniqueKey() {
        return String.valueOf(hashCode());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConfigChangeWorkItem that = (ConfigChangeWorkItem) o;
        return configChangeEvent.equals(that.configChangeEvent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(configChangeEvent);
    }
}
