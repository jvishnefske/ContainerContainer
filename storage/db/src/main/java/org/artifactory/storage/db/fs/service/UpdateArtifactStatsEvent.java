package org.artifactory.storage.db.fs.service;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.artifactory.repo.RepoPath;
import org.artifactory.storage.db.fs.model.MetadataStats;
import org.springframework.context.ApplicationEvent;

import java.util.Map;

/**
 * @author Uriah Levy
 */
@Getter
@EqualsAndHashCode(callSuper = false)
public class UpdateArtifactStatsEvent extends ApplicationEvent {

    private final Map<RepoPath, MetadataStats> pathToStats;

    UpdateArtifactStatsEvent(Object source, Map<RepoPath, MetadataStats> pathToStats) {
        super(source);
        this.pathToStats = pathToStats;
    }
}
