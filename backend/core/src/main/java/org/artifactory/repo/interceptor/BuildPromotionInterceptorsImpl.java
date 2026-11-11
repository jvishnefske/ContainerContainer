package org.artifactory.repo.interceptor;

import lombok.NonNull;
import org.artifactory.config.CentralConfigKey;
import org.artifactory.sapi.interceptor.BuildPromotionInterceptor;
import org.artifactory.spring.Reloadable;
import org.artifactory.storage.db.DbService;
import org.jfrog.build.api.Build;
import org.jfrog.build.api.release.Promotion;
import org.springframework.stereotype.Service;

@Service
@Reloadable(
        beanClass = BuildPromotionInterceptors.class,
        initAfter = DbService.class,
        listenOn = CentralConfigKey.none)
public class BuildPromotionInterceptorsImpl
        extends Interceptors<BuildPromotionInterceptor>
        implements BuildPromotionInterceptors {

    @Override
    public void afterPromote(
            @NonNull Build build,
            @NonNull String buildRepo,
            @NonNull Promotion promotion) {
        for (BuildPromotionInterceptor importInterceptor : this) {
            importInterceptor.afterPromote(build, buildRepo, promotion);
        }
    }
}