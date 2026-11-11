package org.artifactory.repo.remote;

import lombok.Data;
import org.jfrog.common.config.diff.GenerateDiffFunction;


/**
 * @author Tamar Rosen
 */
@Data
@GenerateDiffFunction
public class PropertiesContent {

    public PropertiesContent() {
        super();
    }

    public PropertiesContent(PropertiesContent propertiesContent) {
        this.enabled = propertiesContent.isEnabled();
    }

    private boolean enabled = false;

    /**
     * Checks whether delegation is enabled
     *
     * @return boolean
     */
    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Disables delegation
     */
    public void disable() {
        this.enabled = false;
    }

    /**
     * Enables delegation
     */
    public void enable() {
        this.enabled = true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        PropertiesContent that = (PropertiesContent) o;

        return enabled == that.enabled;
    }

    @Override
    public int hashCode() {
        return (enabled ? 1 : 0);
    }
}
