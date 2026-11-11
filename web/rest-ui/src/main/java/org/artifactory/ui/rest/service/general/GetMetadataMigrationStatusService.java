package org.artifactory.ui.rest.service.general;

import lombok.RequiredArgsConstructor;
import org.artifactory.metadata.service.MetadataEventService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.general.MetadataMigrationStatusModel;
import org.springframework.stereotype.Component;

/**
 * @author Andrey Tashlitsky
 */
@Component
@RequiredArgsConstructor
public class GetMetadataMigrationStatusService implements RestService {

    private final MetadataEventService metadataEventService;

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        boolean migrating = metadataEventService.isMigrating();
        MetadataMigrationStatusModel metadataMigrationStatusModel = new MetadataMigrationStatusModel(migrating);
        response.iModel(metadataMigrationStatusModel);
    }
}
