package org.artifactory.storage.db.upgrades.common;

import jakarta.annotation.Nonnull;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.storage.db.AsyncDatabaseConverterAdapter;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * @author Alexei Vainshtein
 */
public class DatabaseTestConverterAdapter extends AsyncDatabaseConverterAdapter {

    private ScheduledExecutorService executorService;

    public DatabaseTestConverterAdapter(ArtifactoryHome artifactoryHome) {
        super(artifactoryHome);
    }

    @Nonnull
    protected ScheduledExecutorService getScheduledExecutorService() {
        if (executorService == null) {
            executorService = Executors.newScheduledThreadPool(1);
        }
        return executorService;
    }
}
