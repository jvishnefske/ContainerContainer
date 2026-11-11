package org.artifactory.release.bundle.interceptor;

import lombok.NonNull;
import org.artifactory.api.rest.distribution.bundle.models.ReleaseBundleModel;
import org.artifactory.common.interceptor.Interceptor;

/**
 * @author Gavriel Shleypak
 */
public interface ReleaseBundleInterceptor extends Interceptor {

    void onAfterCloseTransaction(@NonNull ReleaseBundleModel releaseBundleModel);

    void onDeleteReleaseBundleRequestStarted(@NonNull ReleaseBundleModel releaseBundleModel);

    void onDeleteReleaseBundleRequestCompleted(@NonNull ReleaseBundleModel releaseBundleModel);

    void onDeleteReleaseBundleRequestFailed(@NonNull ReleaseBundleModel releaseBundleModel,
            @NonNull String errorMessage);
}
