package com.jfrog.build.config;

import com.jfrog.build.acl.bridge.BuildBridgeFromArtifactory;
import com.jfrog.build.acl.bridge.BuildBridgeToArtifactory;
import com.jfrog.build.acl.bridge.BuildFacade;
import com.jfrog.build.bridge.BuildBridgeFromArtifactoryImpl;
import com.jfrog.build.bridge.BuildFacadeImpl;
import com.jfrog.build.command.BuildAddModulesCommand;
import com.jfrog.build.command.BuildCreateCommand;
import com.jfrog.build.command.BuildDeleteCommand;
import com.jfrog.build.command.BuildGetAllCommand;
import com.jfrog.build.command.BuildGetArtifactsByPatternsCommand;
import com.jfrog.build.command.BuildGetByNameCommand;
import com.jfrog.build.command.BuildGetInfoCommand;
import com.jfrog.build.command.BuildPromoteCommand;
import com.jfrog.build.command.BuildRedirectUploadCommand;
import com.jfrog.build.command.BuildRenameCommand;
import com.jfrog.build.command.BuildRetainCommand;
import com.jfrog.build.command.cleanup.BuildDeleteForCleanupCommand;
import com.jfrog.build.command.cleanup.BuildGetNamesForCleanupCommand;
import com.jfrog.build.command.cleanup.BuildGetNumbersForCleanupCommand;
import com.jfrog.build.command.ui.BuildDeleteAllUiCommand;
import com.jfrog.build.command.ui.BuildDeleteUiCommand;
import com.jfrog.build.command.ui.BuildGetAllNamesPagedUiCommand;
import com.jfrog.build.command.ui.BuildGetAllNamesUiCommand;
import com.jfrog.build.command.ui.BuildGetAllUiCommand;
import com.jfrog.build.command.ui.BuildGetArtifactBuildJsonUiCommand;
import com.jfrog.build.command.ui.BuildGetArtifactBuildsUiCommand;
import com.jfrog.build.command.ui.BuildGetArtifactDiffUiCommand;
import com.jfrog.build.command.ui.BuildGetArtifactsUiCommand;
import com.jfrog.build.command.ui.BuildGetBuildDiffUiCommand;
import com.jfrog.build.command.ui.BuildGetBuildIssuesUiCommand;
import com.jfrog.build.command.ui.BuildGetContentUiCommand;
import com.jfrog.build.command.ui.BuildGetDependencyDiffUiCommand;
import com.jfrog.build.command.ui.BuildGetEffectivePermissionByGroupUiCommand;
import com.jfrog.build.command.ui.BuildGetEffectivePermissionByUserUiCommand;
import com.jfrog.build.command.ui.BuildGetEffectivePermissionUiCommand;
import com.jfrog.build.command.ui.BuildGetEnvBuildPropsUiCommand;
import com.jfrog.build.command.ui.BuildGetGeneralInfoUiCommand;
import com.jfrog.build.command.ui.BuildGetHistoryUiCommand;
import com.jfrog.build.command.ui.BuildGetJsonUiCommand;
import com.jfrog.build.command.ui.BuildGetModuleArtifactsUiCommand;
import com.jfrog.build.command.ui.BuildGetModuleDependenciesUiCommand;
import com.jfrog.build.command.ui.BuildGetPrevBuildsUiCommand;
import com.jfrog.build.command.ui.BuildGetProjectInfoUiCommand;
import com.jfrog.build.command.ui.BuildGetPublishedModulesUiCommand;
import com.jfrog.build.command.ui.BuildGetReleaseHistoryUiCommand;
import com.jfrog.build.command.ui.BuildGetRepositoryKeyUiCommand;
import com.jfrog.build.command.ui.BuildGetSystemBuildPropsUiCommand;
import com.jfrog.build.command.ui.BuildSearchIdsByNameUiCommand;
import com.jfrog.build.command.ui.BuildSearchVersionsByNameUiCommand;
import com.jfrog.build.dao.BuildArtifactsDao;
import com.jfrog.build.dao.BuildDependenciesDao;
import com.jfrog.build.dao.BuildModulesDao;
import com.jfrog.build.dao.BuildsDao;
import com.jfrog.build.resource.BuildPublicResource;
import com.jfrog.build.resource.internal.ArtifactBuildsUiResource;
import com.jfrog.build.resource.internal.BuildCleanupResource;
import com.jfrog.build.resource.internal.BuildUiResource;
import com.jfrog.build.service.BuildAqlService;
import com.jfrog.build.service.BuildArtifactService;
import com.jfrog.build.service.BuildCreationService;
import com.jfrog.build.service.BuildDatabaseService;
import com.jfrog.build.service.BuildDeletionService;
import com.jfrog.build.service.BuildDependencyService;
import com.jfrog.build.service.BuildDiffService;
import com.jfrog.build.service.BuildImportService;
import com.jfrog.build.service.BuildModuleService;
import com.jfrog.build.service.BuildPermissionService;
import com.jfrog.build.service.BuildProjectService;
import com.jfrog.build.service.BuildPromotionService;
import com.jfrog.build.service.BuildReadService;
import com.jfrog.build.service.BuildReleasableCollector;
import com.jfrog.build.service.BuildReleaseBundleService;
import com.jfrog.build.service.BuildRenameService;
import com.jfrog.build.service.BuildRepoService;
import com.jfrog.build.service.BuildRetentionService;
import com.jfrog.build.service.BuildSearchService;
import com.jfrog.build.service.BuildSysConfigService;
import com.jfrog.build.service.BuildUrlService;
import com.jfrog.build.tx.BuildTxRunner;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@Configuration
@AllArgsConstructor
public class BuildServiceBeansConfig {

    private final BuildBridgeToArtifactory bridgeToArtifactory;

    @Bean
    public BuildTxRunner buildTxRunner() {
        return new BuildTxRunner();
    }

    @Bean
    public BuildsDao buildsDao(
            @NonNull BuildSysConfigService buildSysConfigService) {
        return new BuildsDao(bridgeToArtifactory, buildSysConfigService);
    }

    @Bean
    public BuildModulesDao buildModulesDao(
            @NonNull BuildSysConfigService buildSysConfigService) {
        return new BuildModulesDao(bridgeToArtifactory, buildSysConfigService);
    }

    @Bean
    public BuildArtifactsDao buildArtifactsDao(
            @NonNull BuildSysConfigService buildSysConfigService) {
        return new BuildArtifactsDao(bridgeToArtifactory, buildSysConfigService);
    }

    @Bean
    public BuildDependenciesDao buildDependenciesDao(
            @NonNull BuildSysConfigService buildSysConfigService) {
        return new BuildDependenciesDao(bridgeToArtifactory, buildSysConfigService);
    }

    @Bean
    public BuildAqlService buildAqlService(
            @NonNull BuildDatabaseService buildDatabaseService) {
        return new BuildAqlService(
                bridgeToArtifactory,
                buildDatabaseService
        );
    }

    @Bean
    public BuildArtifactService buildArtifactService(
            @NonNull BuildAqlService buildAqlService,
            @NonNull BuildPermissionService buildPermissionService,
            @NonNull BuildReadService buildReadService,
            @NonNull BuildRepoService buildRepoService,
            @NonNull BuildSysConfigService buildSysConfigService) {
        return new BuildArtifactService(
                bridgeToArtifactory,
                buildAqlService,
                buildPermissionService,
                buildReadService,
                buildRepoService,
                buildSysConfigService
        );
    }

    @Bean
    public BuildCreationService buildCreationService(
            @NonNull BuildTxRunner buildTxRunner,
            @NonNull BuildPermissionService buildPermissionService,
            @NonNull BuildReadService buildReadService,
            @NonNull BuildRepoService buildRepoService,
            @NonNull BuildRetentionService buildRetentionService,
            @NonNull BuildDatabaseService buildDatabaseService,
            @NonNull BuildSysConfigService buildSysConfigService) {
        return new BuildCreationService(
                bridgeToArtifactory,
                buildTxRunner,
                buildDatabaseService,
                buildPermissionService,
                buildReadService,
                buildRepoService,
                buildRetentionService,
                buildSysConfigService
        );
    }

    @Bean
    public BuildDatabaseService buildDatabaseService(
            @NonNull BuildsDao buildsDao,
            @NonNull BuildModulesDao buildModulesDao,
            @NonNull BuildArtifactsDao buildArtifactsDao,
            @NonNull BuildDependenciesDao buildDependenciesDao) {
        return new BuildDatabaseService(
                bridgeToArtifactory,
                buildsDao,
                buildModulesDao,
                buildArtifactsDao,
                buildDependenciesDao
        );
    }

    @Bean
    public BuildDeletionService buildDeletionService(
            @NonNull BuildTxRunner buildTxRunner,
            @NonNull BuildAqlService buildAqlService,
            @NonNull BuildArtifactService buildArtifactService,
            @NonNull BuildPermissionService buildPermissionService,
            @NonNull BuildReadService buildReadService,
            @NonNull BuildDatabaseService buildDatabaseService,
            @NonNull BuildSysConfigService buildSysConfigService) {
        return new BuildDeletionService(
                bridgeToArtifactory,
                buildTxRunner,
                buildAqlService,
                buildArtifactService,
                buildDatabaseService,
                buildPermissionService,
                buildReadService,
                buildSysConfigService
        );
    }

    @Bean
    public BuildDependencyService buildDependencyService(
            @NonNull BuildPermissionService buildPermissionService,
            @NonNull BuildDatabaseService buildDatabaseService) {
        return new BuildDependencyService(
                bridgeToArtifactory,
                buildDatabaseService,
                buildPermissionService
        );
    }

    @Bean
    public BuildDiffService buildDiffService(
            @NonNull BuildArtifactService buildArtifactService) {
        return new BuildDiffService(buildArtifactService);
    }

    @Bean
    public BuildImportService buildImportService(
            @NonNull BuildTxRunner buildTxRunner,
            @NonNull BuildCreationService buildCreationService,
            @NonNull BuildRepoService buildRepoService,
            @NonNull BuildDatabaseService buildDatabaseService) {
        return new BuildImportService(
                bridgeToArtifactory,
                buildTxRunner,
                buildCreationService,
                buildDatabaseService,
                buildRepoService
        );
    }

    @Bean
    public BuildModuleService buildModuleService(
            @NonNull BuildPermissionService buildPermissionService,
            @NonNull BuildReadService buildReadService,
            @NonNull BuildRepoService buildRepoService,
            @NonNull BuildDatabaseService buildDatabaseService) {
        return new BuildModuleService(
                bridgeToArtifactory,
                buildDatabaseService,
                buildPermissionService,
                buildReadService,
                buildRepoService
        );
    }

    @Bean
    public BuildPermissionService buildPermissionService() {
        return new BuildPermissionService(bridgeToArtifactory);
    }

    @Bean
    public BuildProjectService buildProjectService() {
        return new BuildProjectService(bridgeToArtifactory);
    }

    @Bean
    public BuildPromotionService buildPromotionService(
            @NonNull BuildTxRunner buildTxRunner,
            @NonNull BuildArtifactService buildArtifactService,
            @NonNull BuildCreationService buildCreationService,
            @NonNull BuildDeletionService buildDeletionService,
            @NonNull BuildDependencyService buildDependencyService,
            @NonNull BuildPermissionService buildPermissionService,
            @NonNull BuildReadService buildReadService,
            @NonNull BuildDatabaseService buildDatabaseService,
            @NonNull BuildSysConfigService buildSysConfigService) {
        return new BuildPromotionService(
                bridgeToArtifactory,
                buildTxRunner,
                buildArtifactService,
                buildCreationService,
                buildDatabaseService,
                buildDeletionService,
                buildDependencyService,
                buildPermissionService,
                buildReadService,
                buildSysConfigService
        );
    }

    @Bean
    public BuildReadService buildReadService(
            @NonNull BuildsDao buildsDao,
            @NonNull BuildPermissionService buildPermissionService,
            @NonNull BuildRepoService buildRepoService,
            @NonNull BuildDatabaseService buildDatabaseService) {
        return new BuildReadService(
                bridgeToArtifactory,
                buildsDao,
                buildDatabaseService,
                buildPermissionService,
                buildRepoService
        );
    }

    @Bean
    public BuildReleasableCollector buildReleasableCollector(
            @NonNull BuildArtifactService buildArtifactService,
            @NonNull BuildReadService buildReadService) {
        return new BuildReleasableCollector(
                bridgeToArtifactory,
                buildArtifactService,
                buildReadService
        );
    }

    @Bean
    public BuildReleaseBundleService buildReleaseBundleService(@NonNull BuildsDao buildsDao) {
        return new BuildReleaseBundleService(buildsDao);
    }

    @Bean
    public BuildRenameService buildRenameService(
            @NonNull BuildTxRunner buildTxRunner,
            @NonNull BuildReadService buildReadService,
            @NonNull BuildCreationService buildCreationService,
            @NonNull BuildDeletionService buildDeletionService,
            @NonNull BuildPermissionService buildPermissionService) {
        return new BuildRenameService(
                bridgeToArtifactory,
                buildTxRunner,
                buildReadService,
                buildCreationService,
                buildDeletionService,
                buildPermissionService
        );
    }

    @Bean
    public BuildRepoService buildRepoService() {
        return new BuildRepoService(bridgeToArtifactory);
    }

    @Bean
    public BuildRetentionService buildRetentionService(
            @NonNull BuildDeletionService buildDeletionService,
            @NonNull BuildPermissionService buildPermissionService,
            @NonNull BuildReadService buildReadService,
            @NonNull BuildSysConfigService buildSysConfigService) {
        return new BuildRetentionService(
                bridgeToArtifactory,
                buildDeletionService,
                buildPermissionService,
                buildReadService,
                buildSysConfigService
        );
    }

    @Bean
    public BuildSearchService buildSearchService(
            @NonNull BuildPermissionService buildPermissionService,
            @NonNull BuildDatabaseService buildDatabaseService,
            @NonNull BuildSysConfigService buildSysConfigService) {
        return new BuildSearchService(
                bridgeToArtifactory,
                buildPermissionService,
                buildDatabaseService,
                buildSysConfigService
        );
    }

    @Bean
    public BuildSysConfigService buildSysConfigService() {
        return new BuildSysConfigService(bridgeToArtifactory);
    }

    @Bean
    public BuildUrlService buildUrlService() {
        return new BuildUrlService(bridgeToArtifactory);
    }

    @Bean
    public BuildDeleteForCleanupCommand buildDeleteForCleanupCommand(
            @NonNull BuildRepoService buildRepoService,
            @NonNull BuildProjectService buildProjectService,
            @NonNull BuildDeletionService buildDeletionService) {
        return new BuildDeleteForCleanupCommand(
                buildRepoService,
                buildProjectService,
                buildDeletionService
        );
    }

    @Bean
    public BuildGetNamesForCleanupCommand buildGetNamesForCleanupCommand(
            @NonNull BuildRepoService buildRepoService,
            @NonNull BuildProjectService buildProjectService,
            @NonNull BuildDatabaseService buildDatabaseService) {
        return new BuildGetNamesForCleanupCommand(
                buildRepoService,
                buildProjectService,
                buildDatabaseService
        );
    }

    @Bean
    public BuildGetNumbersForCleanupCommand buildGetNumbersForCleanupCommand(
            @NonNull BuildRepoService buildRepoService,
            @NonNull BuildProjectService buildProjectService,
            @NonNull BuildDatabaseService buildDatabaseService) {
        return new BuildGetNumbersForCleanupCommand(
                buildRepoService,
                buildProjectService,
                buildDatabaseService
        );
    }

    @Bean
    public BuildDeleteAllUiCommand buildDeleteAllUiCommand(
            @NonNull BuildRepoService buildRepoService,
            @NonNull BuildDeletionService buildDeletionService) {
        return new BuildDeleteAllUiCommand(
                buildRepoService,
                buildDeletionService
        );
    }

    @Bean
    public BuildDeleteUiCommand buildDeleteUiCommand(
            @NonNull BuildRepoService buildRepoService,
            @NonNull BuildReadService buildReadService,
            @NonNull BuildDeletionService buildDeletionService,
            @NonNull BuildPermissionService buildPermissionService) {
        return new BuildDeleteUiCommand(
                buildRepoService,
                buildReadService,
                buildDeletionService,
                buildPermissionService
        );
    }

    @Bean
    public BuildGetAllNamesPagedUiCommand buildGetAllNamesPagedUiCommand(
            @NonNull BuildRepoService buildRepoService,
            @NonNull BuildSearchService buildSearchService,
            @NonNull BuildPermissionService buildPermissionService) {
        return new BuildGetAllNamesPagedUiCommand(
                buildRepoService,
                buildSearchService,
                buildPermissionService
        );
    }

    @Bean
    public BuildGetAllNamesUiCommand buildGetAllNamesUiCommand(
            @NonNull BuildReadService buildReadService,
            @NonNull BuildRepoService buildRepoService) {
        return new BuildGetAllNamesUiCommand(
                buildReadService,
                buildRepoService
        );
    }

    @Bean
    public BuildGetAllUiCommand buildGetAllUiCommand(
            @NonNull BuildRepoService buildRepoService,
            @NonNull BuildSearchService buildSearchService,
            @NonNull BuildSysConfigService buildSysConfigService,
            @NonNull BuildPermissionService buildPermissionService) {
        return new BuildGetAllUiCommand(
                buildRepoService,
                buildSearchService,
                buildSysConfigService,
                buildPermissionService
        );
    }

    @Bean
    public BuildGetArtifactBuildJsonUiCommand buildGetArtifactBuildJsonUiCommand(
            @NonNull BuildReadService buildReadService,
            @NonNull BuildRepoService buildRepoService) {
        return new BuildGetArtifactBuildJsonUiCommand(
                buildReadService,
                buildRepoService
        );
    }

    @Bean
    public BuildGetArtifactBuildsUiCommand buildGetArtifactBuildsUiCommand(
            @NonNull BuildArtifactService buildArtifactService,
            @NonNull BuildReadService buildReadService) {
        return new BuildGetArtifactBuildsUiCommand(
                buildArtifactService,
                buildReadService
        );
    }

    @Bean
    public BuildGetArtifactDiffUiCommand buildGetArtifactDiffUiCommand(
            @NonNull BuildRepoService buildRepoService,
            @NonNull BuildModuleService buildModuleService) {
        return new BuildGetArtifactDiffUiCommand(
                buildRepoService,
                buildModuleService
        );
    }

    @Bean
    public BuildGetArtifactsUiCommand buildGetArtifactsUiCommand(
            @NonNull BuildRepoService buildRepoService,
            @NonNull BuildModuleService buildModuleService) {
        return new BuildGetArtifactsUiCommand(
                buildRepoService,
                buildModuleService
        );
    }

    @Bean
    public BuildGetBuildDiffUiCommand buildGetBuildDiffUiCommand(
            @NonNull BuildRepoService buildRepoService,
            @NonNull BuildReadService buildReadService,
            @NonNull BuildDiffService buildDiffService,
            @NonNull BuildPermissionService buildPermissionService) {
        return new BuildGetBuildDiffUiCommand(
                buildRepoService,
                buildReadService,
                buildDiffService,
                buildPermissionService
        );
    }

    @Bean
    public BuildGetBuildIssuesUiCommand buildGetBuildIssuesUiCommand(
            @NonNull BuildRepoService buildRepoService,
            @NonNull BuildReadService buildReadService) {
        return new BuildGetBuildIssuesUiCommand(
                buildRepoService,
                buildReadService
        );
    }

    @Bean
    public BuildGetContentUiCommand buildGetContentUiCommand(
            @NonNull BuildRepoService buildRepoService,
            @NonNull BuildReadService buildReadService,
            @NonNull BuildReleasableCollector buildReleasableCollector) {
        return new BuildGetContentUiCommand(
                buildRepoService,
                buildReadService,
                buildReleasableCollector
        );
    }

    @Bean
    public BuildGetDependencyDiffUiCommand buildGetDependencyDiffUiCommand(
            @NonNull BuildRepoService buildRepoService,
            @NonNull BuildModuleService buildModuleService,
            @NonNull BuildDependencyService buildDependencyService) {
        return new BuildGetDependencyDiffUiCommand(
                buildRepoService,
                buildModuleService,
                buildDependencyService
        );
    }

    @Bean
    public BuildGetEffectivePermissionByGroupUiCommand buildGetEffectivePermissionByGroupUiCommand(
            @NonNull BuildRepoService buildRepoService,
            @NonNull BuildPermissionService buildPermissionService) {
        return new BuildGetEffectivePermissionByGroupUiCommand(
                buildRepoService,
                buildPermissionService
        );
    }

    @Bean
    public BuildGetEffectivePermissionByUserUiCommand buildGetEffectivePermissionByUserUiCommand(
            @NonNull BuildRepoService buildRepoService,
            @NonNull BuildPermissionService buildPermissionService) {
        return new BuildGetEffectivePermissionByUserUiCommand(
                buildRepoService,
                buildPermissionService
        );
    }

    @Bean
    public BuildGetEffectivePermissionUiCommand buildGetEffectivePermissionUiCommand(
            @NonNull BuildRepoService buildRepoService,
            @NonNull BuildPermissionService buildPermissionService) {
        return new BuildGetEffectivePermissionUiCommand(
                buildRepoService,
                buildPermissionService
        );
    }

    @Bean
    public BuildGetEnvBuildPropsUiCommand buildGetEnvBuildPropsUiCommand(
            @NonNull BuildRepoService buildRepoService,
            @NonNull BuildModuleService buildModuleService) {
        return new BuildGetEnvBuildPropsUiCommand(
                buildRepoService,
                buildModuleService
        );
    }

    @Bean
    public BuildGetGeneralInfoUiCommand buildGetGeneralInfoUiCommand(
            @NonNull BuildRepoService buildRepoService,
            @NonNull BuildModuleService buildModuleService) {
        return new BuildGetGeneralInfoUiCommand(
                buildRepoService,
                buildModuleService
        );
    }

    @Bean
    public BuildGetHistoryUiCommand buildGetHistoryUiCommand(
            @NonNull BuildRepoService buildRepoService,
            @NonNull BuildSearchService buildSearchService,
            @NonNull BuildSysConfigService buildSysConfigService) {
        return new BuildGetHistoryUiCommand(
                buildRepoService,
                buildSearchService,
                buildSysConfigService
        );
    }

    @Bean
    public BuildGetJsonUiCommand buildGetJsonUiCommand(
            @NonNull BuildRepoService buildRepoService,
            @NonNull BuildReadService buildReadService) {
        return new BuildGetJsonUiCommand(
                buildRepoService,
                buildReadService
        );
    }

    @Bean
    public BuildGetModuleArtifactsUiCommand buildGetModuleArtifactsUiCommand(
            @NonNull BuildRepoService buildRepoService,
            @NonNull BuildModuleService buildModuleService) {
        return new BuildGetModuleArtifactsUiCommand(
                buildRepoService,
                buildModuleService
        );
    }

    @Bean
    public BuildGetModuleDependenciesUiCommand buildGetModuleDependenciesUiCommand(
            @NonNull BuildRepoService buildRepoService,
            @NonNull BuildModuleService buildModuleService,
            @NonNull BuildDependencyService buildDependencyService) {
        return new BuildGetModuleDependenciesUiCommand(
                buildRepoService,
                buildModuleService,
                buildDependencyService
        );
    }

    @Bean
    public BuildGetPrevBuildsUiCommand buildGetPrevBuildsUiCommand(
            @NonNull BuildRepoService buildRepoService,
            @NonNull BuildModuleService buildModuleService) {
        return new BuildGetPrevBuildsUiCommand(
                buildRepoService,
                buildModuleService
        );
    }

    @Bean
    public BuildGetProjectInfoUiCommand buildGetProjectInfoUiCommand(
            @NonNull BuildDatabaseService buildDatabaseService,
            @NonNull BuildRepoService buildRepoService) {
        return new BuildGetProjectInfoUiCommand(
                bridgeToArtifactory,
                buildDatabaseService,
                buildRepoService
        );
    }

    @Bean
    public BuildGetPublishedModulesUiCommand buildGetPublishedModulesUiCommand(
            @NonNull BuildRepoService buildRepoService,
            @NonNull BuildModuleService buildModuleService) {
        return new BuildGetPublishedModulesUiCommand(
                buildRepoService,
                buildModuleService
        );
    }

    @Bean
    public BuildGetReleaseHistoryUiCommand buildGetReleaseHistoryUiCommand(
            @NonNull BuildRepoService buildRepoService,
            @NonNull BuildReadService buildReadService) {
        return new BuildGetReleaseHistoryUiCommand(
                buildRepoService,
                buildReadService
        );
    }

    @Bean
    public BuildGetRepositoryKeyUiCommand buildGetRepositoryKeyUiCommand(
            @NonNull BuildRepoService buildRepoService) {
        return new BuildGetRepositoryKeyUiCommand(
                buildRepoService
        );
    }

    @Bean
    public BuildGetSystemBuildPropsUiCommand buildGetSystemBuildPropsUiCommand(
            @NonNull BuildRepoService buildRepoService,
            @NonNull BuildModuleService buildModuleService) {
        return new BuildGetSystemBuildPropsUiCommand(
                buildRepoService,
                buildModuleService
        );
    }

    @Bean
    public BuildSearchIdsByNameUiCommand buildSearchIdsByNameUiCommand(
            @NonNull BuildRepoService buildRepoService,
            @NonNull BuildSearchService buildSearchService,
            @NonNull BuildPermissionService buildPermissionService,
            @NonNull BuildSysConfigService buildSysConfigService) {
        return new BuildSearchIdsByNameUiCommand(
                buildRepoService,
                buildSearchService,
                buildPermissionService,
                buildSysConfigService
        );
    }

    @Bean
    public BuildSearchVersionsByNameUiCommand buildSearchVersionsByNameUiCommand(
            @NonNull BuildRepoService buildRepoService,
            @NonNull BuildSearchService buildSearchService,
            @NonNull BuildPermissionService buildPermissionService,
            @NonNull BuildSysConfigService buildSysConfigService) {
        return new BuildSearchVersionsByNameUiCommand(
                buildRepoService,
                buildSearchService,
                buildPermissionService,
                buildSysConfigService
        );
    }

    @Bean
    public BuildAddModulesCommand buildAddModulesCommand(
            @NonNull BuildRepoService buildRepoService,
            @NonNull BuildReadService buildReadService,
            @NonNull BuildCreationService buildCreationService) {
        return new BuildAddModulesCommand(
                buildRepoService,
                buildReadService,
                buildCreationService
        );
    }

    @Bean
    public BuildCreateCommand buildCreateCommand(
            @NonNull BuildRepoService buildRepoService,
            @NonNull BuildCreationService buildCreationService,
            @NonNull BuildSysConfigService buildSysConfigService) {
        return new BuildCreateCommand(
                buildRepoService,
                buildCreationService,
                buildSysConfigService
        );
    }

    @Bean
    public BuildDeleteCommand buildDeleteCommand(
            @NonNull BuildRepoService buildRepoService,
            @NonNull BuildReadService buildReadService,
            @NonNull BuildDeletionService buildDeletionService) {
        return new BuildDeleteCommand(
                buildRepoService,
                buildReadService,
                buildDeletionService
        );
    }

    @Bean
    public BuildGetAllCommand buildGetAllCommand(
            @NonNull BuildUrlService buildUrlService,
            @NonNull BuildRepoService buildRepoService,
            @NonNull BuildDatabaseService buildDatabaseService,
            @NonNull BuildPermissionService buildPermissionService) {
        return new BuildGetAllCommand(
                buildUrlService,
                buildRepoService,
                buildDatabaseService,
                buildPermissionService
        );
    }

    @Bean
    public BuildGetArtifactsByPatternsCommand buildGetArtifactsByPatternsCommand(
            @NonNull BuildUrlService buildUrlService,
            @NonNull BuildArtifactService buildArtifactService) {
        return new BuildGetArtifactsByPatternsCommand(
                buildUrlService,
                buildArtifactService
        );
    }

    @Bean
    public BuildGetByNameCommand buildGetByNameCommand(
            @NonNull BuildUrlService buildUrlService,
            @NonNull BuildRepoService buildRepoService,
            @NonNull BuildReadService buildReadService) {
        return new BuildGetByNameCommand(
                buildUrlService,
                buildRepoService,
                buildReadService
        );
    }

    @Bean
    public BuildGetInfoCommand buildGetInfoCommand(
            @NonNull BuildUrlService buildUrlService,
            @NonNull BuildRepoService buildRepoService,
            @NonNull BuildReadService buildReadService,
            @NonNull BuildDiffService buildDiffService) {
        return new BuildGetInfoCommand(
                buildUrlService,
                buildRepoService,
                buildReadService,
                buildDiffService
        );
    }

    @Bean
    public BuildPromoteCommand buildPromoteCommand(
            @NonNull BuildRepoService buildRepoService,
            @NonNull BuildReadService buildReadService,
            @NonNull BuildPromotionService buildPromotionService) {
        return new BuildPromoteCommand(
                buildRepoService,
                buildReadService,
                buildPromotionService
        );
    }

    @Bean
    public BuildRedirectUploadCommand buildRedirectUploadCommand(
            @NonNull BuildUrlService buildUrlService) {
        return new BuildRedirectUploadCommand(
                buildUrlService
        );
    }

    @Bean
    public BuildRenameCommand buildRenameCommand(
            @NonNull BuildRepoService buildRepoService,
            @NonNull BuildReadService buildReadService,
            @NonNull BuildRenameService buildRenameService,
            @NonNull BuildPermissionService buildPermissionService) {
        return new BuildRenameCommand(
                buildRepoService,
                buildReadService,
                buildRenameService,
                buildPermissionService
        );
    }

    @Bean
    public BuildRetainCommand buildRetainCommand(
            @NonNull BuildRepoService buildRepoService,
            @NonNull BuildRetentionService buildRetentionService,
            @NonNull BuildSysConfigService buildSysConfigService) {
        return new BuildRetainCommand(
                buildRepoService,
                buildRetentionService,
                buildSysConfigService
        );
    }

    @Bean
    public ArtifactBuildsUiResource artifactBuildsUiResource(
            @NonNull BuildGetArtifactBuildJsonUiCommand buildGetArtifactBuildJsonUiCommand,
            @NonNull BuildGetArtifactBuildsUiCommand buildGetArtifactBuildsUiCommand) {
        return new ArtifactBuildsUiResource(
                buildGetArtifactBuildJsonUiCommand,
                buildGetArtifactBuildsUiCommand
        );
    }

    @Bean
    public BuildCleanupResource buildCleanupResource(
            @NonNull BuildDeleteForCleanupCommand buildDeleteForCleanupCommand,
            @NonNull BuildGetNamesForCleanupCommand buildGetNamesForCleanupCommand,
            @NonNull BuildGetNumbersForCleanupCommand buildGetNumbersForCleanupCommand) {
        return new BuildCleanupResource(
                buildDeleteForCleanupCommand,
                buildGetNamesForCleanupCommand,
                buildGetNumbersForCleanupCommand
        );
    }

    @Bean
    public BuildUiResource buildUiResource(
            @NonNull BuildDeleteAllUiCommand buildDeleteAllUiCommand,
            @NonNull BuildDeleteUiCommand buildDeleteUiCommand,
            @NonNull BuildGetAllNamesUiCommand buildGetAllNamesUiCommand,
            @NonNull BuildGetAllNamesPagedUiCommand buildGetAllNamesPagedUiCommand,
            @NonNull BuildGetAllUiCommand buildGetAllUiCommand,
            @NonNull BuildGetArtifactDiffUiCommand buildGetArtifactDiffUiCommand,
            @NonNull BuildGetArtifactsUiCommand buildGetArtifactsUiCommand,
            @NonNull BuildGetBuildDiffUiCommand buildGetBuildDiffUiCommand,
            @NonNull BuildGetBuildIssuesUiCommand buildGetBuildIssuesUiCommand,
            @NonNull BuildGetContentUiCommand buildGetContentUiCommand,
            @NonNull BuildGetDependencyDiffUiCommand buildGetDependencyDiffUiCommand,
            @NonNull BuildGetEffectivePermissionByGroupUiCommand buildGetEffectivePermissionByGroupUiCommand,
            @NonNull BuildGetEffectivePermissionByUserUiCommand buildGetEffectivePermissionByUserUiCommand,
            @NonNull BuildGetEffectivePermissionUiCommand buildGetEffectivePermissionUiCommand,
            @NonNull BuildGetEnvBuildPropsUiCommand buildGetEnvBuildPropsUiCommand,
            @NonNull BuildGetGeneralInfoUiCommand buildGetGeneralInfoUiCommand,
            @NonNull BuildGetHistoryUiCommand buildGetHistoryUiCommand,
            @NonNull BuildGetJsonUiCommand buildGetJsonUiCommand,
            @NonNull BuildGetModuleArtifactsUiCommand buildGetModuleArtifactsUiCommand,
            @NonNull BuildGetModuleDependenciesUiCommand buildGetModuleDependenciesUiCommand,
            @NonNull BuildGetPrevBuildsUiCommand buildGetPrevBuildsUiCommand,
            @NonNull BuildGetProjectInfoUiCommand buildGetProjectInfoUiCommand,
            @NonNull BuildGetPublishedModulesUiCommand buildGetPublishedModulesUiCommand,
            @NonNull BuildGetReleaseHistoryUiCommand buildGetReleaseHistoryUiCommand,
            @NonNull BuildGetRepositoryKeyUiCommand buildGetRepositoryKeyUiCommand,
            @NonNull BuildGetSystemBuildPropsUiCommand buildGetSystemBuildPropsUiCommand,
            @NonNull BuildSearchIdsByNameUiCommand buildSearchIdsByNameUiCommand,
            @NonNull BuildSearchVersionsByNameUiCommand buildSearchVersionsByNameUiCommand) {
        return new BuildUiResource(
                buildDeleteAllUiCommand,
                buildDeleteUiCommand,
                buildGetAllNamesPagedUiCommand,
                buildGetAllNamesUiCommand,
                buildGetAllUiCommand,
                buildGetArtifactDiffUiCommand,
                buildGetArtifactsUiCommand,
                buildGetBuildDiffUiCommand,
                buildGetBuildIssuesUiCommand,
                buildGetContentUiCommand,
                buildGetDependencyDiffUiCommand,
                buildGetEffectivePermissionByGroupUiCommand,
                buildGetEffectivePermissionByUserUiCommand,
                buildGetEffectivePermissionUiCommand,
                buildGetEnvBuildPropsUiCommand,
                buildGetGeneralInfoUiCommand,
                buildGetHistoryUiCommand,
                buildGetJsonUiCommand,
                buildGetModuleArtifactsUiCommand,
                buildGetModuleDependenciesUiCommand,
                buildGetPrevBuildsUiCommand,
                buildGetProjectInfoUiCommand,
                buildGetPublishedModulesUiCommand,
                buildGetReleaseHistoryUiCommand,
                buildGetRepositoryKeyUiCommand,
                buildGetSystemBuildPropsUiCommand,
                buildSearchIdsByNameUiCommand,
                buildSearchVersionsByNameUiCommand
        );
    }

    @Bean
    public BuildPublicResource buildPublicResource(
            @NonNull BuildAddModulesCommand buildAddModulesCommand,
            @NonNull BuildCreateCommand buildCreateCommand,
            @NonNull BuildDeleteCommand buildDeleteCommand,
            @NonNull BuildGetAllCommand buildGetAllCommand,
            @NonNull BuildGetArtifactsByPatternsCommand buildGetArtifactsByPatternsCommand,
            @NonNull BuildGetByNameCommand buildGetByNameCommand,
            @NonNull BuildGetInfoCommand buildGetInfoCommand,
            @NonNull BuildPromoteCommand buildPromoteCommand,
            @NonNull BuildRedirectUploadCommand buildRedirectUploadCommand,
            @NonNull BuildRenameCommand buildRenameCommand,
            @NonNull BuildRetainCommand buildRetainCommand) {
        return new BuildPublicResource(
                buildAddModulesCommand,
                buildCreateCommand,
                buildDeleteCommand,
                buildGetAllCommand,
                buildGetArtifactsByPatternsCommand,
                buildGetByNameCommand,
                buildGetInfoCommand,
                buildPromoteCommand,
                buildRedirectUploadCommand,
                buildRenameCommand,
                buildRetainCommand
        );
    }

    @Bean
    public BuildBridgeFromArtifactory buildBridgeFromArtifactory(
            @NonNull BuildRepoService buildRepoService,
            @NonNull BuildImportService buildImportService) {
        return new BuildBridgeFromArtifactoryImpl(
                buildRepoService,
                buildImportService
        );
    }

    @Bean
    public BuildFacade buildFacade(
            @NonNull BuildArtifactService buildArtifactService,
            @NonNull BuildCreationService buildCreationService,
            @NonNull BuildDatabaseService buildDatabaseService,
            @NonNull BuildDeletionService buildDeletionService,
            @NonNull BuildDependencyService buildDependencyService,
            @NonNull BuildImportService buildImportService,
            @NonNull BuildPermissionService buildPermissionService,
            @NonNull BuildReadService buildReadService,
            @NonNull BuildReleaseBundleService buildReleaseBundleService,
            @NonNull BuildRepoService buildRepoService) {
        return new BuildFacadeImpl(
                buildArtifactService,
                buildCreationService,
                buildDatabaseService,
                buildDeletionService,
                buildDependencyService,
                buildImportService,
                buildPermissionService,
                buildReadService,
                buildReleaseBundleService,
                buildRepoService
        );
    }
}
