package org.artifactory.repo.interceptor.config.ha;

import org.artifactory.config.ConfigurationException;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.service.validation.ArtifactoryVersionException;
import org.artifactory.repo.service.validation.HaArtifactoryVersionAlignmentValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;

import static org.apache.http.HttpStatus.SC_METHOD_NOT_ALLOWED;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

class HaArtifactoryVersionAlignmentRepoConfigInterceptorTest {

    @Mock
    private HaArtifactoryVersionAlignmentValidator haArtifactoryVersionAlignmentValidator;
    @InjectMocks
    private HaArtifactoryVersionAlignmentRepoConfigInterceptor interceptor;
    private AutoCloseable mocks;

    @BeforeEach
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void testOnBeforeSave_Valid() {
        interceptor.onBeforeSave(Map.of());
        verify(haArtifactoryVersionAlignmentValidator).validate();
    }

    @Test
    void testOnBeforeSave_Invalid() {
        doThrow(new ArtifactoryVersionException("Version mismatch")).when(haArtifactoryVersionAlignmentValidator)
                .validate();
        Map<RepoDetailsType, List<RepoConfigChange>> emptyChangesMap = Map.of();
        ConfigurationException exception =
                assertThrows(ConfigurationException.class, () -> interceptor.onBeforeSave(emptyChangesMap));
        Assertions.assertEquals(SC_METHOD_NOT_ALLOWED, exception.getStatusCode());
        Assertions.assertEquals("Version mismatch", exception.getMessage());
    }
}