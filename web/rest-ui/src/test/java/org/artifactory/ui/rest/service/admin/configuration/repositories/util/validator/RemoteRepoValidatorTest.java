package org.artifactory.ui.rest.service.admin.configuration.repositories.util.validator;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.rest.common.exception.RepoConfigException;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteRepositoryConfigModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author arkojd
 */
class RemoteRepoValidatorTest {

    @Mock
    private NugetRemoteRepoValidator mockValidator;

    @Mock
    private RemoteRepoConfig mockRemoteRepoConfig;
    @Mock
    private ArtifactoryHome artifactoryHome;
    @Mock
    private ArtifactorySystemProperties artifactorySystemProperties;
    @Mock
    private ArtifactoryContext artifactoryContext;
    private RemoteRepoPackageValidator remoteRepoPackageValidator;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        when(mockValidator.getRepoType()).thenReturn(RepoType.NuGet);
        when(artifactoryContext.beansForType(RemoteRepoPackageSpecificValidator.class))
                .thenReturn(Map.of(RepoType.NuGet.getType(), mockValidator));
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(artifactorySystemProperties);
        when(artifactorySystemProperties.getProperty(ConstantValues.remoteRepoCreateValidationEnabledPackages)).
                thenReturn("nuget,cargo");
        when(artifactorySystemProperties.getProperty(ConstantValues.remoteRepoUpdateValidationEnabledPackages)).
                thenReturn("nuget");
        ArtifactoryHome.bind(artifactoryHome);
        remoteRepoPackageValidator = new RemoteRepoPackageValidator();
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        ArtifactoryHome.unbind();
        mocks.close();
    }

    @Test
    void testValidateToNotThrowException() throws RepoConfigException {
        when(mockRemoteRepoConfig.getPackageType()).thenReturn(RepoType.NuGet);
        doNothing().when(mockValidator).validate(any(RemoteRepoConfig.class), any(MutableCentralConfigDescriptor.class),
                any(RemoteRepositoryConfigModel.class));
        remoteRepoPackageValidator.validate(mockRemoteRepoConfig, null, null);
        verify(mockValidator, times(1)).validate(mockRemoteRepoConfig, null, null);
    }

    @Test
    void testValidateShouldThrowException() throws RepoConfigException {
        when(mockRemoteRepoConfig.getPackageType()).thenReturn(RepoType.NuGet);
        doThrow(RepoConfigException.class).when(mockValidator).validate(any(), any(), any());
        assertThrows(RepoConfigException.class, () -> {
            remoteRepoPackageValidator.validate(mockRemoteRepoConfig, null, null);
        });
    }

    @Test
    void testGetRemoteRepoCreateValidationEnabledPackages() {
        List<String> packages = remoteRepoPackageValidator.getRemoteRepoCreateValidationEnabledPackages();
        assertTrue(packages.size()==2);
        assertTrue(packages.contains("nuget"));
        assertTrue(packages.contains("cargo"));
    }

    @Test
    void testGetRemoteRepoUpdateValidationEnabledPackages() {
        List<String> packages = remoteRepoPackageValidator.getRemoteRepoUpdateValidationEnabledPackages();
        assertTrue(packages.size()==1);
        assertTrue(packages.contains("nuget"));
    }
}