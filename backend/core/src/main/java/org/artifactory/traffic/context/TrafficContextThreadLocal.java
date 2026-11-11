package org.artifactory.traffic.context;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.experimental.UtilityClass;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.engine.DownloadServiceImpl;
import org.artifactory.engine.UploadServiceImpl;
import org.artifactory.model.RepoConfig;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;

/**
 * @author Andrey Tashlitsky
 */
@UtilityClass
public final class TrafficContextThreadLocal {

    private static final ThreadLocal<TrafficContext> TRAFFIC_CONTEXT_THREAD_LOCAL = new ThreadLocal<>();

    public static void bind() {
        createContextIfNeeded();
    }

    private static void createContextIfNeeded() {
        if (getTrafficContext() == null) {
            TrafficContext trafficContext = new TrafficContext();
            TRAFFIC_CONTEXT_THREAD_LOCAL.set(trafficContext);
        }
    }

    /**
     * Used to bind the traffic context and fill the origin fields based on the given repository key.
     * NOTE: This method is used by {@link UploadServiceImpl#upload} and {@link DownloadServiceImpl#process}.
     * In both of these flows, the traffic log is already handled by the respective services.
     * Therefore, this method disables {@link TrafficContext#trafficLogV3Flow}, so that
     * {@link org.artifactory.webapp.servlet.TrafficFilter} does not log the traffic again.
     */
    public static void bind(@Nonnull String originRepoKey) {
        bind();
        disableTrafficLogV3Flow();
        fillOriginFields(originRepoKey);
    }

    private static void disableTrafficLogV3Flow() {
        TrafficContext trafficContext = getTrafficContext();
        if (trafficContext != null) {
            trafficContext.setTrafficLogV3Flow(false);
        }
    }

    public static void fillOriginFields(@Nonnull String originRepoKey) {
        TrafficContext trafficContext = getTrafficContext();
        if (trafficContext != null) {
            String originProjectKey = ContextHelper.get()
                    .beanForType(ProjectsService.class)
                    .getProjectKeyOfRepository(originRepoKey);
            RepoConfig repoConfig = ContextHelper.get()
                    .beanForType(RepositoryService.class)
                    .repoConfigByKey(originRepoKey);
            trafficContext.setOriginRepoKey(originRepoKey);
            trafficContext.setOriginProjectKey(originProjectKey);
            trafficContext.setOriginRepoConfig(repoConfig);
        }
    }

    public static void unbind() {
        TRAFFIC_CONTEXT_THREAD_LOCAL.remove();
    }

    /**
     * Sets the traffic log v3 context with the given repository key and path.
     * This method should be called when the traffic log v3 flow is initiated.
     *
     * @param repoKey the repository key to set in the context
     * @param path    the path to set in the context
     */
    public static void setTrafficLogV3Context(String repoKey, String path) {
        TrafficContext trafficContext = getTrafficContext();
        if (trafficContext != null) {
            trafficContext.setTrafficLogV3Flow(true);
            String username = ContextHelper.get()
                    .beanForType(SecurityService.class)
                    .currentUsername();
            trafficContext.setUsername(username);
            trafficContext.setRepoPath(RepoPathFactory.create(repoKey, path));
        }
    }

    @Nullable
    public static TrafficContext getTrafficContext() {
        return TRAFFIC_CONTEXT_THREAD_LOCAL.get();
    }

    public static boolean isTrafficLogV3Flow() {
        TrafficContext trafficContext = getTrafficContext();
        if (trafficContext != null) {
            return trafficContext.isTrafficLogV3Flow();
        }
        return false;
    }

    @Nullable
    public static String getUsername() {
        TrafficContext trafficContext = getTrafficContext();
        if (trafficContext != null) {
            return trafficContext.getUsername();
        }
        return null;
    }

    @Nullable
    public static RepoPath getRepoPath() {
        TrafficContext trafficContext = getTrafficContext();
        if (trafficContext != null) {
            return trafficContext.getRepoPath();
        }
        return null;
    }
}
