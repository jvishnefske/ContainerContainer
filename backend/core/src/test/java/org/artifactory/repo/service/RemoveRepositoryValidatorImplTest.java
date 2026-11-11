package org.artifactory.repo.service;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.common.RepoRemovalStatusHolder;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.config.ConfigurationException;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.interceptor.RepoConfigChangeValidator;
import org.artifactory.repo.interceptor.RepoConfigChanges;
import org.artifactory.repo.interceptor.StorageInterceptors;
import org.artifactory.repo.system.SystemRepos;
import org.artifactory.repo.trash.TrashService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_CONFLICT;
import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Slf4j
class RemoveRepositoryValidatorImplTest extends ArtifactoryHomeBoundJUnitTest {

    private static final String REPO_KEY = "repoKey";

    @Mock
    private StorageInterceptors storageInterceptors;
    @Mock
    private RepoConfigChangeValidator repoConfigChangeValidator;
    @Mock
    private ArtifactoryContext artifactoryContext;
    @Mock
    private RepoConfig repoConfig;
    private AutoCloseable mocks;

    private RemoveRepositoryValidatorImpl removeRepositoryValidator;

    @BeforeEach
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        removeRepositoryValidator =
                new RemoveRepositoryValidatorImpl(storageInterceptors,
                        List.of(repoConfigChangeValidator));
        when(repoConfig.getKey()).thenReturn(REPO_KEY);
        when(repoConfig.getRepoType()).thenReturn(LOCAL);
    }

    @AfterEach
    public void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    void testInitForSonar() {
        when(artifactoryContext.beansForType((RepoConfigChangeValidator.class))).thenReturn(Map.of());
        when(artifactoryContext.beanForType((StorageInterceptors.class))).thenReturn(storageInterceptors);
        removeRepositoryValidator.init();
        verify(artifactoryContext).beansForType(any());
        verify(artifactoryContext).beanForType(any());
    }

    @Test
    void testValidate_StorageInterceptorFails_Failure() {
        givenStorageInterceptorValidationFails();

        RepositoryRemovalFailedException exception =
                assertThrows(RepositoryRemovalFailedException.class,
                        () -> removeRepositoryValidator.validate(repoConfig));

        assertEquals(SC_FORBIDDEN, exception.getStatusHolder().getStatusCode());
        assertEquals("error", exception.getStatusHolder().getStatusMsg());
    }

    @Test
    void testValidate_ImportInProgress_Failure() {
        givenReposImportIsInProgress();

        RepositoryRemovalFailedException exception =
                assertThrows(RepositoryRemovalFailedException.class,
                        () -> removeRepositoryValidator.validate(repoConfig));

        assertEquals(SC_CONFLICT, exception.getStatusHolder().getStatusCode());
        assertEquals("Cannot delete repository: '%s', import is still in progress".formatted(REPO_KEY),
                exception.getStatusHolder().getStatusMsg());
    }

    @Test
    void testValidateRepository_ValidatorThrowsConfigException_Failure() {
        givenValidatorThrowsException(new ConfigurationException("Thrown by test", SC_FORBIDDEN));
        RepositoryRemovalFailedException exception =
                assertThrows(RepositoryRemovalFailedException.class,
                        () -> removeRepositoryValidator.validate(repoConfig));
        assertTrue(exception.getStatusHolder().hasWarnings());
        assertEquals(SC_FORBIDDEN, exception.getStatusHolder().getStatusCode());
        assertEquals("Cannot delete repository: '%s', Reason: Thrown by test".formatted(REPO_KEY),
                exception.getStatusHolder().getStatusMsg());
    }

    @ParameterizedTest
    @ValueSource(strings = {TrashService.TRASH_KEY, SystemRepos.SUPPORT_BUNDLE_REPO_KEY, EMPTY})
    void testValidate_RepoKeyCannotDeleteBlankOrTrashOrSupportBundle_Failure(String repoKey) {
        when(repoConfig.getKey()).thenReturn(repoKey);
        RepositoryRemovalFailedException exception =
                assertThrows(RepositoryRemovalFailedException.class,
                        () -> removeRepositoryValidator.validate(repoConfig));

        assertEquals(SC_BAD_REQUEST, exception.getStatusHolder().getStatusCode());
        assertEquals("Cannot delete repository: repository key is blank, trash or support bundle",
                exception.getStatusHolder().getStatusMsg());
    }

    @Test
    void testValidate_RepoKeyCannotBeNull_Failure() {
        testValidate_RepoKeyCannotDeleteBlankOrTrashOrSupportBundle_Failure(null);
    }

    private void givenValidatorThrowsException(Exception e) {
        doThrow(e).when(repoConfigChangeValidator).validate(any(RepoConfigChanges.class));
    }

    private void givenStorageInterceptorValidationFails() {
        doAnswer(invocation -> {
            RepoRemovalStatusHolder statusHolder = invocation.getArgument(1);
            statusHolder.error("error", SC_FORBIDDEN, log);
            return null;
        }).when(storageInterceptors).assertDeleteRepoAllowed(matches(REPO_KEY), any(RepoRemovalStatusHolder.class));
    }

    private void givenReposImportIsInProgress() {
        when(artifactoryContext.isReposImportInProgress()).thenReturn(true);
    }
}