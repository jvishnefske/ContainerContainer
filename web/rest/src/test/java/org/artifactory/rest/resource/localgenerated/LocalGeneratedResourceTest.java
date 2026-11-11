package org.artifactory.rest.resource.localgenerated;

import org.apache.http.HttpStatus;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.repo.cache.expirable.CacheExpiryImpl;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.exception.http.NotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import jakarta.ws.rs.core.Response;
import java.util.List;

import static org.mockito.Mockito.when;

class LocalGeneratedResourceTest {

    @Mock
    private RepositoryService repositoryService;

    @Mock
    private CacheExpiryImpl cacheExpiry;

    private AutoCloseable mocks;

    private LocalGeneratedResource localGeneratedResource;

    @BeforeEach
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        localGeneratedResource = new LocalGeneratedResource(repositoryService, cacheExpiry);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void testPathRepoNotExist() {
        LocalGeneratedPathsModel localGeneratedModel = new LocalGeneratedPathsModel("repo1", List.of("path"));
        Assertions.assertThrows(NotFoundException.class, () -> localGeneratedResource.filterPaths(localGeneratedModel));
    }

    @Test
    void testNoPaths() {
        String repoKey = "repo1";
        LocalRepoDescriptor repoDescriptor = new LocalRepoDescriptor();
        repoDescriptor.setType(RepoType.Generic);
        when(repositoryService.repoDescriptorByKey(repoKey)).thenReturn(repoDescriptor);
        LocalGeneratedPathsModel localGeneratedModel = new LocalGeneratedPathsModel(repoKey, null);
        Assertions.assertThrows(BadRequestException.class,
                () -> localGeneratedResource.filterPaths(localGeneratedModel));
    }

    @Test
    void testPathFilter() {
        String repoKey = "repo1";
        LocalRepoDescriptor repoDescriptor = new LocalRepoDescriptor();
        repoDescriptor.setType(RepoType.Generic);
        when(repositoryService.repoDescriptorByKey(repoKey)).thenReturn(repoDescriptor);
        when(cacheExpiry.isLocalGenerated(RepoType.Generic, repoKey, "localgen")).thenReturn(Boolean.TRUE);
        LocalGeneratedPathsModel
                localGeneratedModel = new LocalGeneratedPathsModel(repoKey, List.of("localgen", "package"));
        try (Response check = localGeneratedResource.filterPaths(localGeneratedModel)) {
            Assertions.assertEquals(HttpStatus.SC_OK, check.getStatus());
            Assertions.assertEquals(LocalGeneratedPathsModel.class, check.getEntity().getClass());
            LocalGeneratedPathsModel model = (LocalGeneratedPathsModel) check.getEntity();
            Assertions.assertEquals(1, model.paths().size());
            Assertions.assertEquals("package", model.paths().get(0));
        }
    }
}