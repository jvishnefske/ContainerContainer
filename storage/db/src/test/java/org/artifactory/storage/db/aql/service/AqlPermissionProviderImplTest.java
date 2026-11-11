package org.artifactory.storage.db.aql.service;

import com.jfrog.build.acl.bridge.BuildFacade;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.OssAddonsManager;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.repo.RepoPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Andrey Tashlitsky
 */
@ExtendWith(MockitoExtension.class)
class AqlPermissionProviderImplTest {

    @Mock
    private ArtifactoryContext context;
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private SecurityService securityService;
    @Mock
    private BuildFacade buildFacade;
    @Mock
    private AddonsManager addonsManager;

    private AqlPermissionProviderImpl provider;

    @BeforeEach
    void setUp() {
        provider = new AqlPermissionProviderImpl();
    }

    @ParameterizedTest
    @MethodSource("canReadScenarios")
    void canRead_shouldHandlePermissionsAndCaching(boolean firstResult, Boolean secondResult,
            Boolean expectedFirstResult, int expectedContextCalls, int expectedServiceCalls) {
        RepoPath repoPath1 = mock(RepoPath.class);
        RepoPath repoPath2 = mock(RepoPath.class);
        try (MockedStatic<ContextHelper> contextHelper = mockStatic(ContextHelper.class)) {
            contextHelper.when(ContextHelper::get).thenReturn(context);
            when(context.getAuthorizationService()).thenReturn(authorizationService);
            when(authorizationService.canRead(repoPath1)).thenReturn(firstResult);
            if (secondResult != null) {
                when(authorizationService.canRead(repoPath2)).thenReturn(secondResult);
            }

            boolean result = provider.canRead(repoPath1);
            if (secondResult != null) {
                provider.canRead(repoPath2);
            }

            if (expectedFirstResult != null) {
                // Single call test - verify result
                if (expectedFirstResult) {
                    assertTrue(result);
                } else {
                    assertFalse(result);
                }
                verify(authorizationService).canRead(repoPath1);
            }
            // Always verify caching behavior
            contextHelper.verify(ContextHelper::get, times(expectedContextCalls));
            verify(context, times(expectedServiceCalls)).getAuthorizationService();
        }
    }

    private static Stream<Arguments> canReadScenarios() {
        return Stream.of(
                // Returns true when authorization service allows read
                Arguments.of(true, null, true, 1, 1),
                // Returns false when authorization service denies read
                Arguments.of(false, null, false, 1, 1),
                // Caches authorization service on multiple calls
                Arguments.of(true, false, null, 1, 1)
        );
    }

    @ParameterizedTest
    @MethodSource("canReadBuildScenarios")
    void canReadBuild_shouldHandlePermissionsAndCaching(
            boolean firstResult,
            Boolean secondResult,
            Boolean expectedFirstResult,
            int expectedContextCalls,
            int expectedServiceCalls) {
        try (MockedStatic<ContextHelper> contextHelper = mockStatic(ContextHelper.class)) {
            contextHelper.when(ContextHelper::get).thenReturn(context);
            when(context.beanForType(BuildFacade.class)).thenReturn(buildFacade);
            when(buildFacade.canReadBuild("build1", "1", "repo1")).thenReturn(firstResult);
            if (secondResult != null) {
                when(buildFacade.canReadBuild("build2", "2", "repo2")).thenReturn(secondResult);
            }

            boolean result = provider.canReadBuild("build1", "1", "repo1");
            if (secondResult != null) {
                provider.canReadBuild("build2", "2", "repo2");
            }

            if (expectedFirstResult != null) {
                // Single call test - verify result
                if (expectedFirstResult) {
                    assertTrue(result);
                } else {
                    assertFalse(result);
                }
                verify(buildFacade).canReadBuild("build1", "1", "repo1");
            }
            // Always verify caching behavior
            contextHelper.verify(ContextHelper::get, times(expectedContextCalls));
            verify(context, times(expectedServiceCalls)).beanForType(BuildFacade.class);
        }
    }

    private static Stream<Arguments> canReadBuildScenarios() {
        return Stream.of(
                // Returns true when build service allows read
                Arguments.of(true, null, true, 1, 1),
                // Returns false when build service denies read
                Arguments.of(false, null, false, 1, 1),
                // Caches build service on multiple calls
                Arguments.of(true, false, null, 1, 1)
        );
    }

    @ParameterizedTest
    @MethodSource("isAdminScenarios")
    void isAdmin_shouldHandlePermissionsAndCaching(boolean firstResult, boolean secondResult,
            Boolean expectedFirstResult, int expectedContextCalls, int expectedServiceCalls, int expectedIsAdminCalls) {
        try (MockedStatic<ContextHelper> contextHelper = mockStatic(ContextHelper.class)) {
            contextHelper.when(ContextHelper::get).thenReturn(context);
            when(context.beanForType(SecurityService.class)).thenReturn(securityService);
            when(securityService.isAdmin()).thenReturn(firstResult, secondResult);

            boolean result = provider.isAdmin();
            if (expectedIsAdminCalls > 1) {
                provider.isAdmin();
            }

            if (expectedFirstResult != null) {
                // Single call test - verify result
                if (expectedFirstResult) {
                    assertTrue(result);
                } else {
                    assertFalse(result);
                }
            }
            // Always verify caching behavior
            contextHelper.verify(ContextHelper::get, times(expectedContextCalls));
            verify(context, times(expectedServiceCalls)).beanForType(SecurityService.class);
        }
    }

    private static Stream<Arguments> isAdminScenarios() {
        return Stream.of(
                // Returns true when security service confirms admin
                Arguments.of(true, false, true, 1, 1, 1),
                // Returns false when security service denies admin
                Arguments.of(false, false, false, 1, 1, 1),
                // Caches security service on multiple calls
                Arguments.of(true, false, null, 1, 1, 2)
        );
    }

    @ParameterizedTest
    @MethodSource("isOssScenarios")
    void isOss_shouldHandleOssDetectionAndCaching(boolean isOssManager, Boolean expectedResult,
            int expectedContextCalls, int expectedServiceCalls, int expectedIsOssCalls) {
        AddonsManager manager = isOssManager ? mock(OssAddonsManager.class) : addonsManager;
        try (MockedStatic<ContextHelper> contextHelper = mockStatic(ContextHelper.class)) {
            contextHelper.when(ContextHelper::get).thenReturn(context);
            when(context.beanForType(AddonsManager.class)).thenReturn(manager);

            boolean result = provider.isOss();
            if (expectedIsOssCalls > 1) {
                provider.isOss();
            }

            if (expectedResult != null) {
                // Single call test - verify result
                if (expectedResult) {
                    assertTrue(result);
                } else {
                    assertFalse(result);
                }
            }
            // Always verify caching behavior
            contextHelper.verify(ContextHelper::get, times(expectedContextCalls));
            verify(context, times(expectedServiceCalls)).beanForType(AddonsManager.class);
        }
    }

    private static Stream<Arguments> isOssScenarios() {
        return Stream.of(
                // Returns true when addons manager is OssAddonsManager
                Arguments.of(true, true, 1, 1, 1),
                // Returns false when addons manager is not OssAddonsManager
                Arguments.of(false, false, 1, 1, 1),
                // Caches addons manager on multiple calls
                Arguments.of(false, null, 1, 1, 2)
        );
    }
}
