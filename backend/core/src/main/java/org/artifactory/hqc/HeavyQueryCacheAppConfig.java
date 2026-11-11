package org.artifactory.hqc;

import jakarta.annotation.Nonnull;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.spring.InternalContextHelper;
import org.artifactory.storage.db.InternalDbService;
import org.jfrog.storage.JdbcHelper;
import org.jfrog.storage.hqc.HeavyQueryCacheAdvice;
import org.jfrog.storage.hqc.HeavyQueryCacheAdvisor;
import org.jfrog.storage.hqc.dao.HeavyQueryCacheDao;
import org.jfrog.storage.hqc.model.HeavyQueryCacheConfig;
import org.jfrog.storage.hqc.service.HeavyQueryCacheConflictsGuardProvider;
import org.jfrog.storage.hqc.service.HeavyQueryCacheService;
import org.jfrog.storage.hqc.service.HeavyQueryCacheServiceImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 *
 * @author Omri Ziv
 */
@Configuration
public class HeavyQueryCacheAppConfig {

    HeavyQueryCacheConflictsGuardProvider conflictsGuardProvider;

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    public HeavyQueryCacheAppConfig(@Nonnull AddonsManager addonsManager) {
        conflictsGuardProvider = () ->
                addonsManager.addonByType(HaAddon.class).getConflictsGuard("hqc");
    }

    @Bean
    public HeavyQueryCacheDao heavyQueryCacheDao(JdbcHelper jdbcHelper, ArtifactoryDbProperties dbProperties) {
        var heavyQueryCache =
                new HeavyQueryCacheDao(jdbcHelper, dbProperties.getDbType());
        heavyQueryCache.setDbOperationsBulkSize(ConstantValues.dbOperationsBulkSize.getLong());
        return heavyQueryCache;
    }

    @Bean
    public HeavyQueryCacheService heavyQueryCacheService(HeavyQueryCacheDao heavyQueryCacheDao) {
        var heavyQueryCacheService = new HeavyQueryCacheServiceImpl(heavyQueryCacheDao,
                conflictsGuardProvider,
                () -> InternalContextHelper.get().beanForType(InternalDbService.class));
        heavyQueryCacheService.setHqcEnabled(ConstantValues.hqcEnabled.getBoolean());
        return heavyQueryCacheService;
    }

    @Bean
    public HeavyQueryCacheAdvice heavyQueryCacheAdvice(HeavyQueryCacheService heavyQueryCacheService,
            HeavyQueryCacheAdvisor heavyQueryCacheAdvisor) {
        var heavyQueryCacheConfig = HeavyQueryCacheConfig.builder()
                .expiryFactor(ConstantValues.hqcExpiryFactor.getInt())
                .minCallableDurationMillis(ConstantValues.hqcMinCallableDurationMillis.getInt())
                .maxBlobSizeKb(ConstantValues.hqcMaxBlobSizeKb.getInt())
                .threadLockPeriod(ConstantValues.hqcRequestTimeoutSecs.getInt())
                .build();

        HeavyQueryCacheAdvice heavyQueryCacheAdvice =
                new HeavyQueryCacheAdvice(heavyQueryCacheService, heavyQueryCacheConfig);
        heavyQueryCacheAdvisor.setAdvice(heavyQueryCacheAdvice);
        return heavyQueryCacheAdvice;
    }
}
