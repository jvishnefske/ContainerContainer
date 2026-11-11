package org.artifactory.metadata.service.interceptor;

import org.artifactory.config.ConfigurationException;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.interceptor.RepoConfigChanges;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_CREATE;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_UPDATE;
import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JemRepoConfigChangeValidatorTest {

    private JemRepoConfigChangeValidator validator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        validator = new JemRepoConfigChangeValidator();
    }

    private static Stream<Arguments> repoConfigActions() {
        return Stream.of(
                Arguments.of(REPO_CREATE),
                Arguments.of(REPO_UPDATE)
        );
    }

    @ParameterizedTest
    @MethodSource("repoConfigActions")
    void testValidateJemFederatedRepos_ShouldThrowException(RepoConfigChange.ConfigAction action) {
        RepoConfigChange change =
                createRepoConfigChange(RepoDetailsType.FEDERATED_REPO, RepoType.Jem.getType(), action);
        RepoConfigChanges changes = new RepoConfigChanges(Map.of(LOCAL, List.of(change)));
        assertThrows(ConfigurationException.class, () -> validator.validate(changes));
    }

    @ParameterizedTest
    @MethodSource("repoConfigActions")
    void testValidateNonFederatedJemRepos_ShouldNotThrowException(RepoConfigChange.ConfigAction action) {
        RepoConfigChange change = createRepoConfigChange(RepoDetailsType.LOCAL_REPO, RepoType.Jem.getType(), action);
        RepoConfigChanges changes = new RepoConfigChanges(Map.of(LOCAL, List.of(change)));
        assertDoesNotThrow(() -> validator.validate(changes));
    }

    @Test
    void testValidateNonJemFederatedRepos_ShouldNotThrowException() {
        RepoConfigChange change =
                createRepoConfigChange(RepoDetailsType.FEDERATED_REPO, RepoType.Maven.getType(), REPO_CREATE);
        RepoConfigChanges changes = new RepoConfigChanges(Map.of(LOCAL, List.of(change)));
        assertDoesNotThrow(() -> validator.validate(changes));
    }

    private RepoConfigChange createRepoConfigChange(String repoType, String packageType,
            RepoConfigChange.ConfigAction action) {
        RepoConfig repoConfig = mock(RepoConfig.class);
        when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.fromString(repoType));
        when(repoConfig.getPackageType()).thenReturn(RepoType.fromType(packageType));
        return new RepoConfigChange("test-repo", action, repoConfig, null);
    }
}