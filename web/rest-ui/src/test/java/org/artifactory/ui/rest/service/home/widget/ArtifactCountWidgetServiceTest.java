package org.artifactory.ui.rest.service.home.widget;

import org.artifactory.api.repo.RepositoryService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.storage.CacheUnAvailableException;
import org.artifactory.storage.StorageSummaryInfo;
import org.artifactory.storage.StorageSummaryService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.ui.rest.model.home.HomeWidgetModel;
import org.artifactory.ui.utils.RequestUtils;
import org.jfrog.storage.hqc.exception.HeavyQueryCacheException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.artifactory.common.ConstantValues.useHomeWidgetArtifactCountV2Cache;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Amir Avidan
 */
@ExtendWith(MockitoExtension.class)
class ArtifactCountWidgetServiceTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private RepositoryService repoService;
    @Mock
    private StorageSummaryService storageSummaryService;
    @Mock
    private ArtifactoryRestRequest request;
    @Mock
    private StorageSummaryInfo storageSummaryInfo;
    @InjectMocks
    private ArtifactCountWidgetService artifactCountWidgetService;
    private ArtifactoryRestResponse response;

    @BeforeEach
    void setUp() {
        response = new ArtifactoryRestResponse();
    }

    @Test
    void testExecuteWithDefaultProject() throws HeavyQueryCacheException, CacheUnAvailableException {
        getOrCreateArtifactoryHomeStub().setProperty(useHomeWidgetArtifactCountV2Cache.getPropertyName(), "false");
        when(RequestUtils.getProjectKeyFromRequest(request)).thenReturn("default");
        when(repoService.getArtifactCount()).thenReturn(100L);
        artifactCountWidgetService.execute(request, response);
        verify(repoService, times(1)).getArtifactCount();
        verify(storageSummaryService, never()).getLocalCache();
        HomeWidgetModel homeWidgetModel = (HomeWidgetModel) response.getIModel();
        assertEquals(100L, homeWidgetModel.getWidgetData().get("artifactCount"));
    }

    @Test
    void testExecuteWithDefaultProjectAndCacheOn() throws HeavyQueryCacheException, CacheUnAvailableException {
        getOrCreateArtifactoryHomeStub().setProperty(useHomeWidgetArtifactCountV2Cache.getPropertyName(), "true");
        String projectName = "default";
        when(RequestUtils.getProjectKeyFromRequest(request)).thenReturn(projectName);
        when(storageSummaryService.getLocalCache()).thenReturn(storageSummaryInfo);
        when(storageSummaryInfo.getTotalFiles()).thenReturn(50L);
        artifactCountWidgetService.execute(request, response);
        verify(repoService, never()).getArtifactCount();
        verify(storageSummaryService, times(1)).getLocalCache();
        HomeWidgetModel homeWidgetModel = (HomeWidgetModel) response.getIModel();
        assertEquals(50L, homeWidgetModel.getWidgetData().get("artifactCount"));
    }

    @Test
    void testExecuteWithNonDefaultProject() throws HeavyQueryCacheException, CacheUnAvailableException {
        String projectName = "nonDefaultProject";
        when(RequestUtils.getProjectKeyFromRequest(request)).thenReturn(projectName);
        Map<String, Long> projectFilesCount = new HashMap<>();
        projectFilesCount.put(projectName, 50L);
        when(storageSummaryService.getLocalCache()).thenReturn(storageSummaryInfo);
        when(storageSummaryInfo.getProjectsTotalFilesCount()).thenReturn(projectFilesCount);
        artifactCountWidgetService.execute(request, response);
        verify(repoService, never()).getArtifactCount();
        verify(storageSummaryService, times(1)).getLocalCache();
        HomeWidgetModel homeWidgetModel = (HomeWidgetModel) response.getIModel();
        assertEquals(50L, homeWidgetModel.getWidgetData().get("artifactCount"));
    }

    @Test
    void testExecuteWithHeavyQueryCacheException() throws HeavyQueryCacheException, CacheUnAvailableException {
        getOrCreateArtifactoryHomeStub().setProperty(useHomeWidgetArtifactCountV2Cache.getPropertyName(), "false");
        when(RequestUtils.getProjectKeyFromRequest(request)).thenReturn("default");
        when(repoService.getArtifactCount()).thenThrow(new HeavyQueryCacheException("Heavy query"));
        when(storageSummaryService.getLocalCache()).thenReturn(storageSummaryInfo);
        when(storageSummaryInfo.getTotalItems()).thenReturn(200L);
        artifactCountWidgetService.execute(request, response);
        verify(repoService, times(1)).getArtifactCount();
        verify(storageSummaryService, times(1)).getLocalCache();
        HomeWidgetModel homeWidgetModel = (HomeWidgetModel) response.getIModel();
        assertEquals(200L, homeWidgetModel.getWidgetData().get("artifactCount"));
    }

    @Test
    void testExecuteWithCacheUnAvailableException() throws HeavyQueryCacheException, CacheUnAvailableException {
        getOrCreateArtifactoryHomeStub().setProperty(useHomeWidgetArtifactCountV2Cache.getPropertyName(), "false");
        when(RequestUtils.getProjectKeyFromRequest(request)).thenReturn("default");
        when(repoService.getArtifactCount()).thenThrow(new CacheUnAvailableException());
        artifactCountWidgetService.execute(request, response);
        verify(repoService, times(1)).getArtifactCount();
        verify(storageSummaryService, never()).getLocalCache();
        HomeWidgetModel homeWidgetModel = (HomeWidgetModel) response.getIModel();
        assertEquals( 0L, homeWidgetModel.getWidgetData().get("artifactCount"));
    }
}