package org.artifactory.ui.rest.model.admin.configuration.repository.federation;

import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * @author Alexei Vainshtein
 */
public class FederationConfigModelTest {

    @Test
    public void testDefaultEnabled() {
        MirrorMemberConfigModel memberConfigModel = new MirrorMemberConfigModel();
        assertTrue(memberConfigModel.isEnabled());
    }

    @Test
    public void testMirrorsToUrls() {
        FederationConfigModel configModel = new FederationConfigModel();
        List<MirrorMemberConfigModel> models = new ArrayList<>();
        addModel(models, "url1");

        addModel(models, "url2");
        configModel.setMirrors(models);

        List<String> urls = configModel.mirrorsToUrls();
        assertEquals(urls.size(), models.size());
        assertTrue(urls.contains("url1"));
        assertTrue(urls.contains("url2"));
    }

    @Test
    public void testProxyValue() {
        FederationConfigModel configModel = new FederationConfigModel();
        configModel.setProxy("proxy");
        assertEquals(configModel.getProxy(), "proxy");
    }

    private void addModel(List<MirrorMemberConfigModel> models, String url) {
        MirrorMemberConfigModel model = new MirrorMemberConfigModel();
        model.setUrl(url);
        models.add(model);
    }
}