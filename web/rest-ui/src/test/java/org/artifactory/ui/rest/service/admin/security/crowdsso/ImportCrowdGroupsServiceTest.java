package org.artifactory.ui.rest.service.admin.security.crowdsso;

import org.artifactory.api.security.UserGroupService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.ui.rest.model.admin.security.crowdsso.CrowdGroupModel;
import org.jfrog.access.client.AccessClientHttpException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class ImportCrowdGroupsServiceTest {

    @Mock
    private UserGroupService userGroupService;
    @Mock
    private ArtifactoryRestRequest request;
    @Mock
    private RestResponse response;

    private ImportCrowdGroupsService importCrowdGroupsService;
    private AutoCloseable autoCloseable;

    @BeforeMethod
    public void setUp() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        importCrowdGroupsService = new ImportCrowdGroupsService();
        importCrowdGroupsService.setUserGroupService(userGroupService);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        autoCloseable.close();
    }

    @Test
    public void executeExceptionDuringGroupCreationVerifyNotBreakMethod() {
        CrowdGroupModel model = new CrowdGroupModel();
        model.setGroupName("TestName");
        model.setImportIntoArtifactory(true);
        model.setExistsInArtifactory(false);
        when(request.getModels()).thenReturn(Collections.singletonList(model));
        when(userGroupService.createGroup(any())).thenThrow(AccessClientHttpException.class);
        importCrowdGroupsService.execute(request, response);
        assertFalse(model.isExistsInArtifactory());
    }

    @Test
    public void executeWithValidGroupVerifySetToExists() {
        CrowdGroupModel model = new CrowdGroupModel();
        model.setGroupName("TestName");
        model.setImportIntoArtifactory(true);
        model.setExistsInArtifactory(false);
        when(request.getModels()).thenReturn(Collections.singletonList(model));
        when(userGroupService.getAllExternalGroups()).thenReturn(Collections.emptyMap());
        importCrowdGroupsService.execute(request, response);
        assertTrue(model.isExistsInArtifactory());
    }

}
