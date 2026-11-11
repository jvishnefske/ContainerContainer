package org.artifactory.ui.rest.resource.artifacts.setmeup;

import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.setmeup.GradleSettingModel;
import org.artifactory.ui.rest.model.setmeup.IvySettingModel;
import org.artifactory.ui.rest.model.setmeup.MavenSettingModel;
import org.artifactory.ui.rest.service.setmeup.SetMeUpServiceFactory;
import org.jfrog.common.JsonUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import jakarta.ws.rs.core.Response;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author BarakH
 */
class SetMeUpResourceTest {

    private SetMeUpResourceSpy setMeUpResource;

    @BeforeEach
    void setUp() {
        setMeUpResource = new SetMeUpResourceSpy();
        setMeUpResource.setMeUpServiceFactory = Mockito.mock(SetMeUpServiceFactory.class);
    }

    @Test
    void testMavenDownload() throws Exception {
        MavenSettingModel mavenSettingModel = new MavenSettingModel();
        mavenSettingModel.setMavenSnippet("snip");
        mavenSettingModel.setSettings("settings");
        String data = JsonUtils.getInstance().writeValueAsString(mavenSettingModel);

        Response response = setMeUpResource.downloadMavenSnippet(data);
        Response response2 = setMeUpResource.generateMavenSnippet(mavenSettingModel);
        assertEquals(response.getEntity().toString(), response2.getEntity().toString());
    }

    @Test
    void testGradleDownload() throws Exception {
        GradleSettingModel gradleSettingModel = new GradleSettingModel();
        gradleSettingModel.setGradleSnippet("snip");
        gradleSettingModel.setPluginRepoKey("repoKey");
        String data = JsonUtils.getInstance().writeValueAsString(gradleSettingModel);

        Response response = setMeUpResource.downloadGradleProperties(data);
        Response response2 = setMeUpResource.generateGradleSnippet(gradleSettingModel);
        assertEquals(response.getEntity().toString(), response2.getEntity().toString());
    }

    @Test
    void testIvyDownload() throws Exception {
        IvySettingModel ivySettingModel = new IvySettingModel();
        ivySettingModel.setIvySnippet("snip");
        ivySettingModel.setSettings("settings");
        String data = JsonUtils.getInstance().writeValueAsString(ivySettingModel);

        Response response = setMeUpResource.generateIvySnippet(data);
        Response response2 = setMeUpResource.generateIvySnippet(ivySettingModel);
        assertEquals(response.getEntity().toString(), response2.getEntity().toString());
    }

    private static class SetMeUpResourceSpy extends SetMeUpResource {
        @Override
        protected <Y> Response runService(RestService<Y> service, Y model) {
            return Response.ok(model).build();
        }
    }
}