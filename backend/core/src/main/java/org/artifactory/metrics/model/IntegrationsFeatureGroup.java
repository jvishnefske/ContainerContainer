package org.artifactory.metrics.model;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.artifactory.api.callhome.FeatureGroup;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * @author Dan Feldman
 */
@NoArgsConstructor
@Data
@EqualsAndHashCode(callSuper=false)
public class IntegrationsFeatureGroup extends FeatureGroup {

    private int count;
    private int uniqueCount;

    public IntegrationsFeatureGroup(String name) {
        super(name);
    }

    @Builder
    public IntegrationsFeatureGroup(String name, Map<String, Object> attributes, List<FeatureGroup> features,
            int count, int uniqueCount) {
        this.name = name;
        this.attributes = attributes;
        this.features = features;
        this.count = count;
        this.uniqueCount = uniqueCount;
    }

    @JsonIgnore
    public boolean isEmpty() {
        return (attributes == null || attributes.isEmpty())
                && (features == null || features.isEmpty())
                && count == 0 && uniqueCount == 0
                && isBlank(name);
    }
}
