package org.artifactory.storage.db.event.service.metadata.model;

import jakarta.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.artifactory.event.EventType;
import org.jfrog.metadata.client.model.MetadataEntity;
import org.jfrog.metadata.client.model.event.MetadataEventEntity;

import java.util.Map;

import static org.artifactory.util.application.ApplicationServiceConfigurationUtil.APPLICATION_SERVICE_ID_PROPERTY_NAME;

/**
 * @author Uriah Levy
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MetadataEvent {
    private MetadataEventEntity metadataEventEntity;
    private EventType eventType;
    private long eventTime;
    private String path;

    private MetadataEntity metadataEntity;
    private Map<String, String> applicationProperties;

    @Nullable
    public String getApplicationKey() {
        if (applicationProperties != null && !applicationProperties.isEmpty()) {
            return applicationProperties.get(APPLICATION_SERVICE_ID_PROPERTY_NAME);
        }
        return null;
    }
}
