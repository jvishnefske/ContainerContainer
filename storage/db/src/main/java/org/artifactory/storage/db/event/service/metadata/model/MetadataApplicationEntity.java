package org.artifactory.storage.db.event.service.metadata.model;

import jakarta.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.jfrog.metadata.client.model.MetadataEntity;
import org.jfrog.metadata.client.model.event.MetadataEventEntity;

import java.util.Map;

/**
 * @author Uriah Levy
 * @author Natalie Berni
 */
@AllArgsConstructor
@Setter
@Getter
public class MetadataApplicationEntity {
    private MetadataEventEntity metadataEventEntity;
    private MetadataEntity metadataEntity;
    @Nullable
    private Map<String, String> applicationProperties;
}
