package org.artifactory.lifecycle.converter.postinit.v112;

import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.security.SecurityService;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.version.ArtifactoryServiceVersion;
import org.artifactory.version.CompoundVersionDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.Stream;

import static org.artifactory.descriptor.repo.RepoType.Generic;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v7930;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v7940;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v7990;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * @author Andrey Tashlitsky
 */
@ExtendWith(MockitoExtension.class)
class RestoreObservabilityReposToDefaultConfigConverterTest extends ArtifactoryHomeBoundJUnitTest {

    private static final String JFROG_LOGS = "jfrog-logs";
    private static final String JFROG_USAGE_LOGS = "jfrog-usage-logs";
    private static final String JFROG_BILLING_LOGS = "jfrog-billing-logs";

    private final RestoreObservabilityReposToDefaultConfigConverter converter =
            new RestoreObservabilityReposToDefaultConfigConverter(v7990.get(), v7990.get());
    @Mock
    private ArtifactoryContext context;
    @Mock
    private RepositoryConfigService repositoryConfigService;
    @Mock
    private SecurityService securityService;

    private LocalRepoConfig jfrogLogs;
    private LocalRepoConfig jfrogUsageLogs;
    private LocalRepoConfig jfrogBillingLogs;

    @BeforeEach
    void setUp() {
        ArtifactoryContextThreadBinder.bind(context);
        jfrogLogs = LocalRepoConfig.create(JFROG_LOGS, Generic);
        jfrogLogs.getSecurityConfig().setXrayIndex(true);
        jfrogUsageLogs = LocalRepoConfig.create(JFROG_USAGE_LOGS, Generic);
        jfrogUsageLogs.getSecurityConfig().setXrayIndex(true);
        jfrogBillingLogs = LocalRepoConfig.create(JFROG_BILLING_LOGS, Generic);
        jfrogBillingLogs.getSecurityConfig().setXrayIndex(true);
    }

    @AfterEach
    void tearDown() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void testConvert() {
        doReturn(repositoryConfigService).when(context).beanForType(RepositoryConfigService.class);
        doReturn(securityService).when(context).beanForType(SecurityService.class);
        doReturn(jfrogLogs).when(repositoryConfigService).getRepoConfigByKey(JFROG_LOGS);
        doReturn(jfrogUsageLogs).when(repositoryConfigService).getRepoConfigByKey(JFROG_USAGE_LOGS);
        doReturn(jfrogBillingLogs).when(repositoryConfigService).getRepoConfigByKey(JFROG_BILLING_LOGS);

        CompoundVersionDetails compoundVersionDetails = mock(CompoundVersionDetails.class);
        converter.convert(compoundVersionDetails, compoundVersionDetails);

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(securityService).doAsSystem(runnableCaptor.capture());
        runnableCaptor.getValue().run();
        ArgumentCaptor<List<LocalRepoConfig>> listCaptor = ArgumentCaptor.forClass(List.class);
        verify(repositoryConfigService).bulkUpsertRepoConfigs(listCaptor.capture(), eq(true));

        listCaptor.getValue().forEach(
                repoConfig -> assertFalse(repoConfig.getSecurityConfig().isXrayIndex())
        );
    }

    @ParameterizedTest
    @MethodSource("sourceTargetVersions")
    void testIsInterested(ArtifactoryServiceVersion source, ArtifactoryServiceVersion target, boolean interested) {
        CompoundVersionDetails sourceDetails = mock(CompoundVersionDetails.class);
        CompoundVersionDetails targetDetails = mock(CompoundVersionDetails.class);
        doReturn(source).when(sourceDetails).getVersion();
        doReturn(target).when(targetDetails).getVersion();
        assertEquals(converter.isInterested(sourceDetails, targetDetails), interested);
    }

    private static Stream<Arguments> sourceTargetVersions() {
        return Stream.of(
                Arguments.of(v7940.get(), v7940.get(), false),
                Arguments.of(v7930.get(), v7940.get(), false),
                Arguments.of(v7940.get(), v7990.get(), true)
        );
    }
}
