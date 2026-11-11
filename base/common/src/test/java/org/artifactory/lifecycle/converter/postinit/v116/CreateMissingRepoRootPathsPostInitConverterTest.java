package org.artifactory.lifecycle.converter.postinit.v116;

import org.artifactory.api.config.InternalRepositoryConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ConstantValues;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.version.ArtifactoryServiceVersion;
import org.artifactory.version.CompoundVersionDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateMissingRepoRootPathsPostInitConverterTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private ArtifactoryContext context;

    @Mock
    private InternalRepositoryConfigService repositoryConfigService;

    @Mock
    private CompoundVersionDetails source;

    @Mock
    private CompoundVersionDetails target;

    private CreateMissingRepoRootPathsPostInitConverter converter;
    private AutoCloseable autoCloseable;

    @BeforeEach
    void setUp() {
        autoCloseable = org.mockito.MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(context);
        converter = new CreateMissingRepoRootPathsPostInitConverter(null);
    }

    private void stubSourceVersionSoParentIsInterestedReturnsTrue() {
        ArtifactoryServiceVersion sourceVersion = mock(ArtifactoryServiceVersion.class);
        when(sourceVersion.after(nullable(ArtifactoryServiceVersion.class))).thenReturn(true);
        when(sourceVersion.before(nullable(ArtifactoryServiceVersion.class))).thenReturn(true);
        when(source.getVersion()).thenReturn(sourceVersion);
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        autoCloseable.close();
    }

    @Test
    void isInterested_shouldReturnTrue_whenFeatureEnabled() {
        // Arrange - parent isInterested requires source.getVersion() to be in range
        stubSourceVersionSoParentIsInterestedReturnsTrue();
        getBound().setProperty(ConstantValues.repoRootAtomicCreation, "true");

        // Act & Assert
        assertTrue(converter.isInterested(source, target));
    }

    @Test
    void isInterested_shouldReturnFalse_whenFeatureDisabled() {
        // Arrange - parent isInterested requires source.getVersion() to be in range
        stubSourceVersionSoParentIsInterestedReturnsTrue();
        getBound().setProperty(ConstantValues.repoRootAtomicCreation, "false");

        // Act & Assert
        assertFalse(converter.isInterested(source, target));
    }

    @Test
    void convert_shouldCallService() {
        // Arrange - convert() is only called if isInterested() returns true
        when(context.beanForType(InternalRepositoryConfigService.class)).thenReturn(repositoryConfigService);
        when(repositoryConfigService.createMissingRepoRootFolders()).thenReturn(0);

        // Act
        converter.convert(source, target);

        // Assert - service method was called
        verify(repositoryConfigService).createMissingRepoRootFolders();
    }

    @Test
    void assertConversionPrecondition_shouldNotThrow() {
        // No preconditions required
        converter.assertConversionPrecondition(null, null, null);
    }
}
