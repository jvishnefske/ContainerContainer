package org.artifactory.lifecycle.storage.db.servers.event;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.artifactory.storage.db.servers.model.ArtifactoryServer;
import org.springframework.context.ApplicationEvent;

import java.util.List;

/**
 * @author Andrey Tashlitsky
 */
@Value
@EqualsAndHashCode(callSuper = false)
public class ServersUpdatedEvent extends ApplicationEvent {

    List<ArtifactoryServer> updatedServers;

    public ServersUpdatedEvent(Object source, List<ArtifactoryServer> updatedServers) {
        super(source);
        this.updatedServers = updatedServers;
    }
}
