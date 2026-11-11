package org.artifactory.ui.rest.model.continuous.dtos;

import com.google.common.collect.Lists;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.ui.utils.RequestUtils;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static com.jfrog.build.acl.util.BuildConstants.BUILD_INFO_REPO_KEY_SUFFIX;
import static org.artifactory.repo.system.SystemRepos.BUILDINFO_REPO_KEY;
import static org.mockito.Mockito.when;

/**
 * @author Mor Merhav
 */
public class ContinueBuildDtoTest {

    private MockitoSession mockitoSession;
    @Mock
    private ArtifactoryRestRequest restRequestMock;
    @Mock
    private UriInfo uriInfo;
    @Mock
    private ContinueBuildDto.ProjectKeyToBuildRepoConverter projectKeyToBuildRepoConverterMock;

    @BeforeMethod
    public void setUp() {
        mockitoSession = Mockito.mockitoSession()
                .strictness(Strictness.STRICT_STUBS)
                .initMocks(this)
                .startMocking();
    }

    @AfterMethod
    public void tearDown() {
        mockitoSession.finishMocking();
    }

    @Test
    public void testBlankProjectKey() {
        when(uriInfo.getQueryParameters()).thenReturn(new MultivaluedHashMap<>());
        when(restRequestMock.getUriInfo()).thenReturn(uriInfo);
        ContinueBuildDto continueBuildDto = new ContinueBuildDto(restRequestMock, projectKeyToBuildRepoConverterMock);
        Assert.assertEquals(continueBuildDto.buildRepo, BUILDINFO_REPO_KEY);
    }

    @Test
    public void testAllProjectKey() {
        MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
        params.put(RequestUtils.PROJECT_KEY_PARAM, Lists.newArrayList(RequestUtils.PROJECT_VALUE_ALL_PARAM));
        when(uriInfo.getQueryParameters()).thenReturn(params);
        when(restRequestMock.getUriInfo()).thenReturn(uriInfo);
        ContinueBuildDto continueBuildDto = new ContinueBuildDto(restRequestMock, projectKeyToBuildRepoConverterMock);
        Assert.assertEquals(continueBuildDto.buildRepo, StringUtils.EMPTY);
    }

    @Test
    public void testCustomProjectKey() {
        String customProjKey = "myProj";
        String buildRepo = customProjKey + BUILD_INFO_REPO_KEY_SUFFIX;
        MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
        params.put(RequestUtils.PROJECT_KEY_PARAM, Lists.newArrayList(customProjKey));
        when(uriInfo.getQueryParameters()).thenReturn(params);
        when(restRequestMock.getUriInfo()).thenReturn(uriInfo);
        when(projectKeyToBuildRepoConverterMock.toBuildRepo(customProjKey)).thenReturn(buildRepo);
        ContinueBuildDto continueBuildDto = new ContinueBuildDto(restRequestMock, projectKeyToBuildRepoConverterMock);
        Assert.assertEquals(continueBuildDto.buildRepo, buildRepo);
    }
}