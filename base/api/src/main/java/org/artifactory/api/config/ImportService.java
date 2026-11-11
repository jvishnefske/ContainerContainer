package org.artifactory.api.config;

import org.artifactory.async.Async;
import org.artifactory.sapi.common.ImportSettings;

public interface ImportService {
    void importSync(ImportSettings importSettings);

    @Async
    void importAsync(ImportSettings importSettings);
}
