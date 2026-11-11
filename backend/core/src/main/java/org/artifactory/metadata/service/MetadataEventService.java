package org.artifactory.metadata.service;

import jakarta.ws.rs.core.Response;
import org.artifactory.api.config.ImportableExportable;
import org.artifactory.async.Async;
import org.artifactory.event.work.NodeEventOperator;
import org.artifactory.repo.RepoPath;
import org.artifactory.spring.ReloadableBean;
import org.jfrog.client.util.ServicelistLoader;
import org.jfrog.metadata.client.MetadataClient;

import java.util.List;

/**
 * A business service that handles Metadata Events. Responsible for starting the event pipeline and initializing
 * the {@link MetadataClient}. Additionally, this service implements the {@link NodeEventOperator} which provides
 * the logic for handling Metadata events.
 *
 * @author Uriah Levy
 */
public interface MetadataEventService extends NodeEventOperator, ReloadableBean, ImportableExportable {

    MetadataClient getMetadataClient();

    @Async
    void reindexAsync(List<String> reindexPaths, boolean deleteFirst);

    Response reindexSync(List<String> reindexPaths, boolean deleteFirst);

    @Async
    void statsRecreateAsync(List<String> statsCalculationPaths);

    Response statsRecreateSync(List<String> statsCalculationPaths);

    /**
     * Sends a delete request to the metadata server containing the requested path
     */
    void deletePathFromMetadataServerUsingMetadataClient(RepoPath repoPath);

    String getMigrationCursorId();

    String getEventPipelineCursorId();

    void refreshMetadataClient(ServicelistLoader servicelistLoader);
}
