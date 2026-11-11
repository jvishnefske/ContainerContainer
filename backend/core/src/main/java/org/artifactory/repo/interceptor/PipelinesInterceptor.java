package org.artifactory.repo.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.pipelines.InternalPipelinesService;
import org.artifactory.repo.interceptor.storage.StorageInterceptorAdapter;

import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.artifactory.repo.system.SystemRepos.PIPEINFO_REPO_KEY;

/**
 * @author Alexei Vainshtein
 */
@Slf4j
public class PipelinesInterceptor extends StorageInterceptorAdapter {

    @Override
    public void assertDeleteRepoAllowed(String repoKey, MutableStatusHolder status) {
        if (PIPEINFO_REPO_KEY.equals(repoKey)) {
            status.error("Deletion of Pipe-Info repository " + repoKey + " is not allowed.", SC_FORBIDDEN, log);
            return;
        }
        InternalPipelinesService pipelinesService = ContextHelper.get().beanForType(InternalPipelinesService.class);
        SecurityService securityService = ContextHelper.get().beanForType(SecurityService.class);
        if (pipelinesService.isPipeInfoRepo(repoKey) && !securityService.isAdmin()) {
            status.error("Deletion of Pipe-Info repository '" + repoKey + "' is restricted only for super admin.",
                    SC_FORBIDDEN, log);
        }
    }
}
