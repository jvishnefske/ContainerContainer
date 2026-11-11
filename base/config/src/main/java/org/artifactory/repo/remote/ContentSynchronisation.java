package org.artifactory.repo.remote;

import lombok.Data;
import org.jfrog.common.config.diff.GenerateDiffFunction;

import java.util.Objects;

/**
 * @author Tamar Rosen
 */
@Data
@GenerateDiffFunction
public class ContentSynchronisation {
    private boolean enabled = false;
    private StatisticsContent statistics;
    private PropertiesContent properties;
    private SourceContent source;

    public ContentSynchronisation() {
        this.statistics = new StatisticsContent();
        this.properties = new PropertiesContent();
        this.source = new SourceContent();
    }

    public ContentSynchronisation(ContentSynchronisation contentSynchronisation) {
        this.enabled = contentSynchronisation.isEnabled();
        this.statistics = new StatisticsContent(contentSynchronisation.getStatistics());
        this.properties = new PropertiesContent(contentSynchronisation.getProperties());
        this.source = new SourceContent(contentSynchronisation.getSource());
    }

    /**
     * Enables delegation when SmartRepo discovered
     */
    public void enable() {
        enabled = true;
    }

    /**
     * Disables delegation
     */
    public void disable() {
        enabled = false;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Checks whether delegation is enabled on container level
     *
     * @return boolean
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Returns delegate representing statistics
     *
     * @return {@link StatisticsContent}
     */
    public StatisticsContent getStatistics() {
        return statistics;
    }

    /**
     * Returns delegate representing properties
     *
     * @return {@link PropertiesContent}
     */
    public PropertiesContent getProperties() {
        return properties;
    }

    /**
     * Returns delegate representing source remote content
     *
     * @return {@link SourceContent}
     */
    public SourceContent getSource() {
        return source;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ContentSynchronisation that = (ContentSynchronisation) o;

        if (enabled != that.enabled) {
            return false;
        }
        if (!Objects.equals(properties, that.properties)) {
            return false;
        }
        if (!Objects.equals(statistics, that.statistics)) {
            return false;
        }
        return Objects.equals(source, that.source);
    }

    @Override
    public int hashCode() {
        int result = (enabled ? 1 : 0);
        result = 13 * result + (statistics != null ? statistics.hashCode() : 0);
        result = 17 * result + (properties != null ? properties.hashCode() : 0);
        result = 31 * result + (source != null ? source.hashCode() : 0);
        return result;
    }
}
