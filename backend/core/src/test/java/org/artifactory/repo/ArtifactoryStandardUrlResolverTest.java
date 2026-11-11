package org.artifactory.repo;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class ArtifactoryStandardUrlResolverTest {

    @Test(dataProvider = "urlProvider")
    public void testNoRestApi(String url, String expectedBaseUrl, String expectedRepoKey) {
        var resolver = new ArtifactoryStandardUrlResolver(url);
        assertEquals(resolver.getBaseUrl(), expectedBaseUrl);
        assertEquals(resolver.getRepoKey(), expectedRepoKey);
    }

    @DataProvider
    private Object[][] urlProvider() {
        return new Object[][] {
                {"artifactory/repoKey", "artifactory", "repoKey"},
                {"artifactory/api/conan/repoKey", "artifactory", "repoKey"},
                {"artifactory/api/alpine/repoKey", "artifactory/api/alpine", "repoKey"},
                {"https://nixdemo.jfrogdev.org/artifactory/api/nix/n-nix-local",
                        "https://nixdemo.jfrogdev.org/artifactory", "n-nix-local"}
        };
    }
}