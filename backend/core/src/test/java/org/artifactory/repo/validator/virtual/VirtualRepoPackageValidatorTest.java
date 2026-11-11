package org.artifactory.repo.validator.virtual;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.VirtualRepoConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

/**
 * @author Einas Ashkar
 */
class VirtualRepoPackageValidatorTest {
    @Mock
    private ArtifactoryContext artifactoryContextMock;
    @Mock
    private ContextHelper contextHelper;
    private AutoCloseable mocks;
    @Mock
    private HuggingFaceRepoConfigValidator huggingFaceValidator;


    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        // Mock beansForType to return a map with a HuggingFace validator
        ArtifactoryContextThreadBinder.bind(artifactoryContextMock);
        Map<String, VirtualRepoPackageSpecificValidator> validators = new HashMap<>();
        Mockito.when(huggingFaceValidator.getRepoType()).thenReturn(RepoType.HuggingFaceML);
        validators.put(RepoType.HuggingFaceML.getType(), huggingFaceValidator);
        Mockito.when(artifactoryContextMock.beansForType(VirtualRepoPackageSpecificValidator.class))
                .thenReturn(validators);

    }

    @AfterEach
    public void cleanup() throws Exception {
        mocks.close();
    }

    @Test
    void ValidateWithHuggingFaceVirtualConfigTest() {
        VirtualRepoConfig virtualRepo = VirtualRepoConfig.create("repoKey", RepoType.HuggingFaceML);
        VirtualRepoPackageValidator validator = new VirtualRepoPackageValidator();
        assertDoesNotThrow(() -> validator.validate(virtualRepo));
        // Verify that the HuggingFaceRepoConfigValidator.validate was called once
        Mockito.verify(huggingFaceValidator, times(1)).validate(virtualRepo);
    }

    @Test
    void ValidateWithNpmVirtualConfig_NoValidatorTest() {
        VirtualRepoConfig virtualRepo = VirtualRepoConfig.create("repoKey", RepoType.Npm);
        VirtualRepoPackageValidator validator = new VirtualRepoPackageValidator();
        assertDoesNotThrow(() -> validator.validate(virtualRepo));
        // No validator should be called for Npm
        Mockito.verify(huggingFaceValidator, never()).validate(Mockito.any());
    }
}
