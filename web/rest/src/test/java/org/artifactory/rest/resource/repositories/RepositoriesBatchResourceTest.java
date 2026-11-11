package org.artifactory.rest.resource.repositories;

import jakarta.ws.rs.core.HttpHeaders;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.rest.RestAddon;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author Dvir Twina
 */
class RepositoriesBatchResourceTest {
    @InjectMocks
    @Spy
    private RepositoriesBatchResource repositoriesBatchResource;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private RestAddon restAddon;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        Mockito.when(addonsManager.addonByType(RestAddon.class)).thenReturn(restAddon);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void createRepositories() {
        repositoriesBatchResource.createRepositories(List.of(), mock(HttpHeaders.class));
        verify(restAddon, times(1)).createRepositories(anyList(), any());
    }

    @Test
    void getRepositoriesByKeys() {
        repositoriesBatchResource.getRepositoriesByKeys(List.of(), mock(HttpHeaders.class));
        verify(restAddon, times(1)).getRepositoryConfigurations(anyList(), any());
    }

    @Test
    void updateRepositories() {
        repositoriesBatchResource.updateRepositories(List.of(), mock(HttpHeaders.class));
        verify(restAddon, times(1)).updateRepositories(anyList(), any());
    }
}