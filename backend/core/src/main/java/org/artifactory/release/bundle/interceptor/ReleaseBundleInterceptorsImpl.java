package org.artifactory.release.bundle.interceptor;

import lombok.NonNull;
import org.artifactory.api.rest.distribution.bundle.models.ReleaseBundleModel;
import org.artifactory.config.CentralConfigKey;
import org.artifactory.repo.interceptor.Interceptors;
import org.artifactory.spring.Reloadable;
import org.artifactory.storage.db.DbService;
import org.springframework.stereotype.Service;

/**
 * @author Gavriel Shleypak
 */
@Service
@Reloadable(beanClass = ReleaseBundleInterceptors.class,
        initAfter = DbService.class,
        listenOn = CentralConfigKey.none)
public class ReleaseBundleInterceptorsImpl extends Interceptors<ReleaseBundleInterceptor>
        implements ReleaseBundleInterceptors {
    @Override
    public void onAfterCloseTransaction(@NonNull ReleaseBundleModel releaseBundleModel) {
        for (ReleaseBundleInterceptor releaseBundleInterceptor : this) {
            releaseBundleInterceptor.onAfterCloseTransaction(releaseBundleModel);
        }
    }

    @Override
    public void onDeleteReleaseBundleRequestStarted(@NonNull ReleaseBundleModel releaseBundleModel) {
        for (ReleaseBundleInterceptor releaseBundleInterceptor : this) {
            releaseBundleInterceptor.onDeleteReleaseBundleRequestStarted(releaseBundleModel);
        }
    }

    @Override
    public void onDeleteReleaseBundleRequestCompleted(@NonNull ReleaseBundleModel releaseBundleModel) {
        for (ReleaseBundleInterceptor releaseBundleInterceptor : this) {
            releaseBundleInterceptor.onDeleteReleaseBundleRequestCompleted(releaseBundleModel);
        }
    }

    @Override
    public void onDeleteReleaseBundleRequestFailed(@NonNull ReleaseBundleModel releaseBundleModel,
            @NonNull String errorMessage) {
        for (ReleaseBundleInterceptor releaseBundleInterceptor : this) {
            releaseBundleInterceptor.onDeleteReleaseBundleRequestFailed(releaseBundleModel, errorMessage);
        }
    }

}
