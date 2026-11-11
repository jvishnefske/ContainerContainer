package org.artifactory.sapi.interceptor;

import lombok.NonNull;
import org.artifactory.common.interceptor.Interceptor;
import org.jfrog.build.api.Build;
import org.jfrog.build.api.release.Promotion;

public interface BuildPromotionInterceptor extends Interceptor {

    void afterPromote(
            @NonNull Build build,
            @NonNull String buildRepo,
            @NonNull Promotion promotion);
}
