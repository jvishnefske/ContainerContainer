package org.artifactory.ph;

import com.jfrog.ph.acl.listener.artifact.PackageArtifactEventListener;
import com.jfrog.ph.acl.listener.config.PackageConfigChangedEventListener;
import com.jfrog.ph.acl.listener.config.PackageConfigChangedEventType;
import com.jfrog.ph.acl.listener.config.PackageRepoConfigListenerContext;
import com.jfrog.ph.acl.listener.repository.PackageRepositoryEventListener;
import com.jfrog.ph.acl.model.PackageArtifact;
import com.jfrog.ph.acl.model.PackageAuthenticationRequestContext;
import com.jfrog.ph.acl.resource.PackageApiEndPoint;
import com.jfrog.ph.acl.service.PackageAnalyticsService;
import com.jfrog.ph.acl.service.PackageArtifactService;
import com.jfrog.ph.acl.service.PackageArtifactoryConfigService;
import com.jfrog.ph.acl.service.PackageAuthenticationAdapter;
import com.jfrog.ph.acl.service.PackageCurationService;
import com.jfrog.ph.acl.service.PackageEventService;
import com.jfrog.ph.acl.service.PackageHeavyQueryCacheService;
import com.jfrog.ph.acl.service.PackageMetadataClient;
import com.jfrog.ph.acl.service.PackageNugetCache;
import com.jfrog.ph.acl.service.PackageNugetSearch;
import com.jfrog.ph.acl.service.PackageOAuthHandler;
import com.jfrog.ph.acl.service.PackageRepoIndexCalculatorService;
import com.jfrog.ph.acl.service.PackageRepoService;
import com.jfrog.ph.acl.service.PackageResourceServiceAdapter;
import com.jfrog.ph.acl.service.PackageSearchArtifactService;
import com.jfrog.ph.acl.service.PackageSecurityService;
import com.jfrog.ph.acl.service.PackageSignedUrlService;
import com.jfrog.ph.acl.service.PackageSpecificationAdapter;
import com.jfrog.ph.acl.service.PackageTaskExecutorSupplier;
import com.jfrog.ph.acl.service.PackageUploadService;
import com.jfrog.ph.acl.service.migration.RepoMigrationsHistoryPersistence;
import com.jfrog.ph.acl.service.migration.RepoMigrationsPersistence;
import com.jfrog.ph.acl.virtual.resolution.PackageRepo;
import com.jfrog.ph.acl.virtual.resolution.PackageRequestContext;
import com.jfrog.ph.acl.virtual.resolution.PackageVirtualResolutionAdapter;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Priority;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Response;
import lombok.NonNull;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.curation.CurationService;
import org.artifactory.addon.curation.packages.PackagesCurationCompliantVersionService;
import org.artifactory.addon.oauth.OAuthHandler;
import org.artifactory.api.properties.PropertiesService;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.request.DownloadService;
import org.artifactory.api.request.UploadService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.aql.AqlService;
import org.artifactory.authentication.ArtifactoryAuthenticationContextFilter;
import org.artifactory.common.guard.ConflictsGuardProvider;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.repo.PackageType;
import org.artifactory.common.repo.RepoInfo;
import org.artifactory.common.resource.BaseResource;
import org.artifactory.config.ConfigurationChangesInterceptor;
import org.artifactory.confprop.ConfProps;
import org.artifactory.event.queue.QueueService;
import org.artifactory.metrics.backoffice.ArtifactoryBackofficeMetricLogger;
import org.artifactory.ph.curation.PackagesCurationCompliantVersionServiceImpl;
import org.artifactory.ph.selector.IndexingSelector;
import org.artifactory.repo.interceptor.storage.StorageInterceptorAdapter;
import org.artifactory.sh.acl.ArtifactoryBinaryService;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.service.trash.NonTrashableItem;
import org.artifactory.sapi.interceptor.StorageAggregationInterceptor;
import org.artifactory.schedule.CachedThreadPoolTaskExecutor;
import org.artifactory.schedule.TaskService;
import org.artifactory.security.InternalSecurityService;
import org.artifactory.security.access.AccessService;
import org.artifactory.security.props.auth.OauthManager;
import org.artifactory.security.token.ArtifactoryTokenProvider;
import org.artifactory.signature.SignedUrlService;
import org.artifactory.spring.ConditionalOnMissingBean;
import org.artifactory.storage.db.DbService;
import org.artifactory.storage.db.fs.dao.PackageNugetSearchDaoImpl;
import org.artifactory.storage.db.fs.dao.RepoMigrationsDaoImpl;
import org.artifactory.storage.db.fs.dao.RepoMigrationsHistoryDaoImpl;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.artifactory.storage.security.service.UserGroupStoreService;
import org.artifactory.traffic.TrafficService;
import org.jfrog.storage.hqc.service.HeavyQueryCacheService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.Ordered;
import org.springframework.security.authentication.AuthenticationManager;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Beans configuration class
 */
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
@Configuration
public class ArtifactoryPackageBeansConfig {

    /**
     * Expose {@link org.artifactory.common.property.ArtifactorySystemProperties} as spring bean type of
     * {@link ConfProps}
     */
    @Bean
    public ConfProps getConfProps() {
        return ArtifactoryHome.get().getArtifactoryProperties();
    }


    @Bean
    public PackageRepoService packageRepoService(InternalRepositoryService repositoryService,
            PropertiesService propertiesService) {
        return new PackageRepoServiceImpl(repositoryService, propertiesService);
    }

    @Bean
    public PackageArtifactService packageDownloadService(DownloadService downloadService,
            InternalRepositoryService repositoryService) {
        return new PackageArtifactServiceImpl(downloadService, repositoryService);
    }

    @Bean
    public PackageUploadService packageUploadService(InternalRepositoryService repositoryService,
            UploadService uploadService, SecurityService securityService, TrafficService trafficService) {
        return new PackageUploadServiceImpl(repositoryService, uploadService, securityService, trafficService);
    }

    @Bean
    public StorageInterceptorAdapter packageStorageInterceptorAdapter(PackageArtifactEventListener packagesListener,
            InternalRepositoryService repositoryService, PackageSpecificationAdapter packageSpecificationAdapter) {
        return new PackageStorageInterceptorImpl(packagesListener, repositoryService, packageSpecificationAdapter);
    }

    @Bean
    public ConfigurationChangesInterceptor packageConfigurationChangesInterceptor(
            PackageConfigChangedEventListener packageConfigChangedEventListener,
            PackageRepoService packageRepoService) {
        return new PackageConfigurationChangesInterceptorImpl(packageConfigChangedEventListener, packageRepoService);
    }

    @Bean
    public StorageAggregationInterceptor repoImportInterceptorAdapter(
            PackageRepositoryEventListener packageRepositoryEventListener,
            PackageSpecificationAdapter packageSpecificationAdapter, PackageRepoService packageRepoService) {
        return new PackageRepoImportInterceptorImpl(packageRepositoryEventListener, packageSpecificationAdapter,
                packageRepoService);
    }

    @Bean
    public NonTrashableItem packageMetadataNonTrashableInterceptor(
            PackageSpecificationAdapter packageSpecificationAdapter) {
        return new PackageMetadataNonTrashableItem(packageSpecificationAdapter);
    }

    /**
     * We are using the Qualifier here because spring will load RealmAwareAuthenticationManager otherwise.
     * RT using PasswordDecryptingManager when using beansForType(AuthenticationManager.class) due the logic in
     * ArtifactoryApplicationContext
     */
    @Bean
    public PackageSecurityService packageSecurityService(InternalSecurityService securityService,
            AuthorizationService authorizationService, AccessService accessService,
            @Qualifier("passwordDecryptingManager") AuthenticationManager authenticationManager,
            UserGroupStoreService userGroupStoreService, OauthManager oauthManager,
            ArtifactoryTokenProvider tokenProvider) {
        return new PackageSecurityServiceImpl(securityService, authorizationService, accessService,
                authenticationManager, userGroupStoreService, oauthManager, tokenProvider);
    }

    @Bean
    public PackageSearchArtifactService packageSearchArtifactService(AqlService aqlService,
            AuthorizationService authorizationService, PackageRepoService repoService,
            PackageNugetSearch packageNugetSearch, RepositoryConfigService repositoryConfigService,
            ArtifactoryBinaryService binaryService, DbService dbService) {
        return new PackageSearchArtifactServiceImpl(aqlService, authorizationService, repoService,
                packageNugetSearch, repositoryConfigService, binaryService, dbService);
    }

    @Bean
    public PackageNugetSearch packageNugetSearch(ArtifactoryJdbcHelper jdbcHelper, DbService dbService) {
        return new PackageNugetSearchDaoImpl(jdbcHelper, dbService);
    }

    @Bean
    public PackageSpecification packageSpecification(PackageSpecificationAdapter packageSpecificationAdapter) {
        return new PackageSpecificationImpl(packageSpecificationAdapter);
    }

    @Bean
    public PackageResourceService packageResourceService(PackageResourceServiceAdapter packageResourceServiceAdapter,
            PackageSpecificationAdapter packageSpecificationAdapter) {
        return new PackageResourceServiceImpl(packageResourceServiceAdapter, packageSpecificationAdapter);
    }

    @Bean
    public ArtifactoryAuthenticationContextFilter packageAuthenticationFilter(
            PackageAuthenticationAdapter packageAuthenticationAdapter) {
        return new PackageAuthenticationFilterImpl(packageAuthenticationAdapter);
    }

    @Bean
    @ConditionalOnMissingBean(PackageAuthenticationAdapter.class)
    public PackageAuthenticationAdapter packageAuthenticationAdapter() {
        return request -> PackageAuthenticationRequestContext.builder().build();
    }

    @Bean
    public PackageArtifactoryConfigService packageRepoDescriptorService(
            InternalRepositoryService internalRepositoryService) {
        return new PackageArtifactoryConfigServiceImpl(internalRepositoryService);
    }

    @Bean
    public PackageVirtualResolutionService packageVirtualResolutionService(
            PackageVirtualResolutionAdapter packageVirtualResolutionAdapter) {
        return new PackageVirtualResolutionServiceImpl(packageVirtualResolutionAdapter);
    }

    @Bean
    PackageEventService packageEventService(QueueService queueService) {
        return new PackageEventServiceImpl(queueService);
    }

    @Bean
    PackageTaskExecutorSupplier packageTaskExecutorSupplier(CachedThreadPoolTaskExecutor executor) {
        return new PackageTaskExecutorSupplierImpl(executor);
    }

    @Bean
    PackageOAuthHandler packageOAuthHandler(OAuthHandler oAuthHandler) {
        return new PackageOAuthHandlerImpl(oAuthHandler);
    }

    @Bean
    PackageAnalyticsService packageAnalyticsService(AddonsManager addonsManager) {
        return new PackageAnalyticsServiceImpl(addonsManager, new ArtifactoryBackofficeMetricLogger());
    }

    @Bean
    IndexingSelector indexingSelector(PackageRepoService packageRepoService,
            PackageSpecificationAdapter packageSpecificationAdapter, AddonsManager addonsManager,
            PackageRepoIndexCalculatorService repoIndexCalculatorService, AuthorizationService authorizationService) {
        return new PackageIndexingSelectorImpl(packageRepoService, packageSpecificationAdapter, addonsManager,
                repoIndexCalculatorService, authorizationService);
    }

    @Bean
    public PackageArchiveService packageArchiveService(AddonsManager addonsManager) {
        return new PackageArchiveServiceImpl(addonsManager);
    }

    @Bean
    PackageMetadataClient packageMetadataClient(InternalRepositoryService repositoryService) {
        return new PackageMetadataClientImpl(repositoryService);
    }

    @Bean
    PackageSignedUrlService packageSignedUrlService(SignedUrlService signedUrlService) {
        return new PackageSignedUrlServiceImpl(signedUrlService);
    }

    /**
     * PackageHeavyQueryCacheService bean for read through cache. The bean is proxied by HeavyQueryCache mechanism.
     * After the very first call to this service, the result will be cached in the HeavyQueryCache in case
     * responseTime/responseSize meets configuration requirements. The next calls will be served from the cache
     * if available.
     */
    @Bean
    PackageHeavyQueryCacheService packageHeavyQueryCacheService(HeavyQueryCacheService heavyQueryCacheService) {
        return new PackageHeavyQueryCacheServiceImpl(heavyQueryCacheService);
    }

    @Bean
    PackageHandlerJobManager packageHandlerJobManager(TaskService taskService) {
        return new PackageHandlerJobManagerImpl(taskService);
    }

    @Bean
    PackageCurationService packageCurationService(@Lazy CurationService curationService,
            RepositoryService repositoryService) {
        return new PackageCurationServiceImpl(curationService, repositoryService);
    }

    @Bean
    PackagesCurationCompliantVersionService packagesCurationCompliantVersionService(
            PackageCurationService packageCurationService,
            PackageArtifactService packageArtifactService, PackageUploadService packageUploadService,
            PackageRepoService packageRepoService, PackageSecurityService packageSecurityService,
            ConflictsGuardProvider<String> conflictsGuardProvider) {
        return new PackagesCurationCompliantVersionServiceImpl<>(packageCurationService, packageArtifactService,
                packageUploadService, packageRepoService, packageSecurityService, conflictsGuardProvider);
    }

    /**
     * Configuration class for default beans that should be loaded only in None PRO run
     * Must have the lowest priority since has condition on missing bean.
     * We want to wait for the real beans a chance to be loaded, and if not, we load the default beans.
     */
    @Configuration
    @Priority(Ordered.LOWEST_PRECEDENCE)
    public static class PackageOssConfig {

        /**
         * Note: The method name must be the same as the method name of the real impl.
         * See
         * (com.jfrog.ph.config.PackageBeansConfig)}
         */
        @Bean
        @ConditionalOnMissingBean(PackageArtifactEventListener.class)
        public PackageArtifactEventListener packageArtifactEventListener() {
            return new PackageArtifactEventListener() {
            };
        }

        @Bean
        @ConditionalOnMissingBean(PackageRepositoryEventListener.class)
        public PackageRepositoryEventListener packageRepositoryEventListener() {
            return eventContext -> {

            };
        }

        @Bean
        public PackageNugetCache packageNugetCache() {
            return new DummyPackageNugetCacheImpl();
        }

        /**
         * Note: The method name must be the same as the method name of the real impl.
         * @see com.jfrog.ph.config.PackageBeansConfig
         */
        @Bean
        @ConditionalOnMissingBean(PackageConfigChangedEventListener.class)
        public PackageConfigChangedEventListener packageConfigChangedEventListener() {
            return new PackageConfigChangedEventListener() {
                @Override
                public void onGlobalConfigChange(@NonNull PackageConfigChangedEventType packageConfigChangedEventType) {
                    // noop
                }

                @Override
                public void onRepoConfigChange(@NonNull PackageRepoConfigListenerContext context) {
                    // noop
                }
            };
        }

        /**
         * Note: The method name must be the same as the method name of the real impl.
         * @see com.jfrog.ph.service.InternalPackageHandlerService
         * @see com.jfrog.ph.acl.service.PackageRepoService
         * @see com.jfrog.ph.acl.service.PackageDownloadService
         * @see com.jfrog.ph.acl.service.PackageUploadService
         * @see com.jfrog.ph.service.PackageHandlerConflictsGuardService
         * @see org.artifactory.confprop.ConfProps
         */
        @Bean
        @ConditionalOnMissingBean(PackageVirtualResolutionAdapter.class)
        public PackageVirtualResolutionAdapter packageVirtualAdapter() {
            return new PackageVirtualResolutionAdapter() {

                @Override
                public boolean shouldUsePackageVirtualResolution(String virtualRepoKey) {
                    return false;
                }

                @Override
                public BaseResource getItemInfoFromVirtual(PackageRepo virtualRepo, PackageRequestContext context) {
                    return null;
                }
            };
        }

        /**
         * Note: The method name must be the same as the method name of the real impl.
         * See
         * (com.jfrog.ph.service.InternalPackageHandlerService)}
         */
        @Bean
        @ConditionalOnMissingBean(PackageRepoIndexCalculatorService.class)
        public PackageRepoIndexCalculatorService packageRepoIndexCalculatorService() {
            return new PackageRepoIndexCalculatorService() {
                @Override
                public void reindex(PackageType packageType, String repoKey) {
                    // noop
                }

                @Override
                public void reindexSpecificPath(PackageType packageType, String repoKey, String path) {
                    // noop
                }
            };
        }

        /**
         * Note: The method name must be the same as the method name of the real impl.
         * See
         * (com.jfrog.ph.service.InternalPackageHandlerService)}
         */
        @Bean
        @ConditionalOnMissingBean(PackageSpecificationAdapter.class)
        public PackageSpecificationAdapter packageSpecificationAdapter() {
            return new PackageSpecificationAdapter() {

                @Override
                public boolean isPackageArtifact(@Nonnull String repoKey, @Nonnull String path) {
                    return false;
                }

                @Override
                public boolean isExpirable(String repoKey, @NonNull PackageType packageType,
                        @NonNull String path) {
                    return false;
                }

                @Override
                public boolean shouldExcludeFromReplication(@Nonnull PackageType packageType, @Nonnull String path) {
                    return false;
                }

                @Override
                public boolean isLocallyGeneratedProperty(@Nonnull PackageType packageType,
                        @Nonnull String propertyKey) {
                    return false;
                }

                @Override
                public boolean isSupportedPackage(PackageType packageType) {
                    return false;
                }

                @Override
                public boolean isSupportedPackage(String repoKey) {
                    return false;
                }

                @Override
                public boolean hasReindex(RepoInfo repoInfo) {
                    return false;
                }

                @Override
                public boolean isViewable(String repoKey, String path) {
                    return false;
                }

                @Override
                public boolean shouldVerifyRemoteArtifact(PackageArtifact artifact) {
                    return false;
                }

                @Override
                public void verifyRemoteArtifactNotCorrupted(PackageArtifact artifact, InputStream is) {
                    // noop
                }

                @Override
                public boolean shouldMarkOriginalPropertiesInBeforeEvents(String repoKey, String artifactPath) {
                    return false;
                }

                @Override
                public Set<String> excludePropertyValueFromMirroring(@NonNull String propertyKey,
                        @NonNull Set<String> propValues, PackageType packageType) {
                    return null;
                }

                @Override
                public Map<String, List<String>> getVirtualAllowedRepos(Map<String, String> packageTypeConfig,
                        Map<String, List<String>> repos) {
                    return repos;
                }
            };
        }

        /**
         * Note: The method name must be the same as the method name of the real impl.
         * See
         * (com.jfrog.ph.service.InternalPackageHandlerService)}
         */
        @Bean
        @ConditionalOnMissingBean(PackageResourceServiceAdapter.class)
        public PackageResourceServiceAdapter packageResourceServiceAdapter() {
            return new PackageResourceServiceAdapter() {

                @Override
                public Response reindex(@NonNull PackageType packageType, @NonNull String repoKey, Boolean async) {
                    return null;
                }

                @Override
                public Object getVersionedSubResource(@NonNull PackageType packageType, String version,
                        @NonNull String repoKey, HttpServletRequest request) {
                    return null;
                }

                @Override
                public Object getNonVersionedSubResource(@NonNull PackageType packageType, @NonNull String context,
                        HttpServletRequest request) {
                    return null;
                }

                @Override
                public PackageApiEndPoint getPackageEndPointSubResource(@NonNull PackageType packageType,
                        String beanIdentifier, @NonNull String context, HttpServletRequest request) {
                    return null;
                }

                @Override
                public Response getPackageInfo(String repoKey, String path) {
                    return null;
                }

                @Override
                public Response getVersionNativeReadme(String type, String fullRepoPath) {
                    return null;
                }
            };
        }
    }

    @Bean
    public RepoMigrationsPersistence repoMigrationsPersistence(ArtifactoryJdbcHelper jdbcHelper,
            DbService dbService) {
        return new RepoMigrationsDaoImpl(jdbcHelper, dbService);
    }

    @Bean
    public RepoMigrationsHistoryPersistence repoMigrationsHistoryPersistence(
            ArtifactoryJdbcHelper jdbcHelper) {
        return new RepoMigrationsHistoryDaoImpl(jdbcHelper);
    }
}