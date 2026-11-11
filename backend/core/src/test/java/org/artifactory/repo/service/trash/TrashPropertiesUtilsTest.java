package org.artifactory.repo.service.trash;

import org.artifactory.api.properties.PropertiesService;
import org.artifactory.repo.RepoPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import static org.artifactory.repo.trash.TrashService.PROP_DELETED_BY;
import static org.artifactory.repo.trash.TrashService.PROP_TRASH_TIME;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * Unit tests for TrashPropertiesUtils
 */
class TrashPropertiesUtilsTest {

    @Mock
    private PropertiesService propertiesService;
    
    @Mock
    private RepoPath repoPath;
    
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void testCleanupTrashProperties() {
        // When
        TrashPropertiesUtils.cleanupTrashProperties(propertiesService, repoPath);
        
        // Then
        verify(propertiesService).deletePropertyRecursively(repoPath, PROP_TRASH_TIME, false);
        verify(propertiesService).deletePropertyRecursively(repoPath, PROP_DELETED_BY, false);
    }
}
