package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes;

import org.artifactory.activity.CurrentActivity;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.rest.constant.RestApiGroup;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.fetch.ArtifactsFetchBulkStrategy;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.fetch.ArtifactsFetchDirectBulkStrategy;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.service.ArtifactFetchService;
import org.artifactory.ui.rest.model.continuous.dtos.ContinueArtifactsDto;
import org.jfrog.commons.continues.ContinueIndexPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Spy;

import static org.artifactory.common.ConstantValues.treeBrowserStreamEnabled;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author Hussein Alshqerat
 */
class PaginatedJunctionNodeTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private ArtifactoryContext context;
    @Spy
    private ArtifactFetchService artifactFetchService;
    @Mock
    private CentralConfigService centralConfigService;
    private AutoCloseable autoCloseable;
    private AutoCloseable scope;

    @BeforeEach
    void setup() {
        autoCloseable = openMocks(this);
        when(context.beanForType(ArtifactFetchService.class)).thenReturn(artifactFetchService);
        when(context.beanForType(CentralConfigService.class)).thenReturn(centralConfigService);
        ArtifactoryContextThreadBinder.bind(context);
        scope = CurrentActivity.newScope();
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(treeBrowserStreamEnabled.getPropertyName(), "FALSE");
    }

    @AfterEach
    void cleanup() throws Exception {
        autoCloseable.close();
        scope.close();
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void testIsArchiveRequest() {
        PaginatedJunctionNode paginatedJunctionNode = new PaginatedJunctionNode();
        when(centralConfigService.getMutableDescriptor()).thenReturn(mock(MutableCentralConfigDescriptor.class));
        paginatedJunctionNode.setContinueArtifactsDto(
                new ContinueArtifactsDto("repo", "path", "local", "Generic", "", true, false));

        assertTrue(paginatedJunctionNode.isArchiveExpendRequest());
    }

    @Test
    void testChooseDirectStrategy() {
        PaginatedJunctionNode paginatedJunctionNode = new PaginatedJunctionNode();
        when(centralConfigService.getMutableDescriptor()).thenReturn(mock(MutableCentralConfigDescriptor.class));
        ContinueIndexPage continueIndexPage = new ContinueIndexPage();
        ArtifactFilter artifactFilter = new ArtifactFilter(continueIndexPage);
        artifactFilter.setMustInclude("cool");
        paginatedJunctionNode.setContinueArtifactsDto(
                new ContinueArtifactsDto("repo", "path", "local", "Generic", "", true, false));

        assertInstanceOf(ArtifactsFetchDirectBulkStrategy.class, paginatedJunctionNode.chooseStrategy(artifactFilter,
                false));
    }

    @Test
    void testChooseDirectBulkStrategy() {
        PaginatedJunctionNode paginatedJunctionNode = new PaginatedJunctionNode();
        when(centralConfigService.getMutableDescriptor()).thenReturn(mock(MutableCentralConfigDescriptor.class));
        ContinueIndexPage continueIndexPage = new ContinueIndexPage();
        ArtifactFilter artifactFilter = new ArtifactFilter(continueIndexPage);
        artifactFilter.setMustInclude("");
        paginatedJunctionNode.setContinueArtifactsDto(
                new ContinueArtifactsDto("repo", "path", "local", "Generic", "", true, false));

        assertInstanceOf(ArtifactsFetchBulkStrategy.class, paginatedJunctionNode.chooseStrategy(artifactFilter, false));
    }

    @Test
    void testFetchItems() {
        PaginatedJunctionNode paginatedJunctionNode = new PaginatedJunctionNode();
        when(centralConfigService.getMutableDescriptor()).thenReturn(mock(MutableCentralConfigDescriptor.class));
        paginatedJunctionNode.setContinueArtifactsDto(
                new ContinueArtifactsDto("repo", "path", "local", "Generic", "", true, false));
        paginatedJunctionNode.fetchItemTypeData(false, true);

        verify(artifactFetchService, times(1)).fetchItems(any(), any(), anyBoolean(), anyBoolean());
    }

    @Test
    void testFetchItemsStreamEnabled() {
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.treeBrowserStreamEnabled, "true");
        PaginatedJunctionNode paginatedJunctionNode = new PaginatedJunctionNode();
        when(centralConfigService.getMutableDescriptor()).thenReturn(mock(MutableCentralConfigDescriptor.class));
        paginatedJunctionNode.setContinueArtifactsDto(
                new ContinueArtifactsDto("repo", "path", "local", "Generic", "", true, false));
        paginatedJunctionNode.fetchItemTypeData(false, true);

        verify(artifactFetchService, times(1)).fetchItemsStreamBased(any(), anyBoolean(), anyBoolean());
    }

    @Test
    void testFetchItemsActivityContextLabeling() {
        PaginatedJunctionNode paginatedJunctionNode = new PaginatedJunctionNode();
        when(centralConfigService.getMutableDescriptor()).thenReturn(mock(MutableCentralConfigDescriptor.class));
        ContinueArtifactsDto dto = new ContinueArtifactsDto("repo", "path", "local", "Generic", "", true, false);
        dto.setMustInclude("cool");
        paginatedJunctionNode.setContinueArtifactsDto(dto);
        paginatedJunctionNode.fetchItemTypeData(false, true);

        assertEquals("generic", CurrentActivity.PACKAGE.get());
        assertEquals(RestApiGroup.BROWSE_SHOW_IN_TREE_API, CurrentActivity.GROUP.get());
    }
}