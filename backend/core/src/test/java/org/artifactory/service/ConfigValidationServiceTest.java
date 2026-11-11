package org.artifactory.service;

import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.repo.P2Configuration;
import org.artifactory.descriptor.repo.RepoLayout;
import org.artifactory.test.ArtifactoryHomeStub;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;

/**
 * Tests for {@link ConfigValidationService}
 *
 * @author Tomer Slutsky
 */
public class ConfigValidationServiceTest {

    @DataProvider
    public static Object[][] repoNames() {
        return new Object[][] {
                {"\u0000"},
                {"works\u0000"},
                {"\u0000alsoworks"},
                {"this\u0000too"}

        };
    }

    @BeforeClass
    public void init() {
        ArtifactoryHome artifactoryHome = new ArtifactoryHomeStub(getClass().getName());
        ArtifactoryHome.bind(artifactoryHome);
    }

    @AfterClass
    public void afterClass() {
        ArtifactoryHome.unbind();
    }

    @Test(dataProvider = "repoNames", expectedExceptions = IllegalStateException.class,
            expectedExceptionsMessageRegExp =
                    ".* Unicode is not " +
                            "supported")
    public void testRepoLayoutFailsWithUnicode(String name) {
        RepoLayout repoLayout = new RepoLayout();
        repoLayout.setName(name);
        ConfigValidationService configValidationService = new ConfigValidationService();
        configValidationService.validate(repoLayout);
    }

    @Test(expectedExceptions = IllegalStateException.class, expectedExceptionsMessageRegExp = ".* Unicode is not " +
            "supported")
    public void testRepoLayoutMultipleViolations() {
        RepoLayout repoLayout = new RepoLayout();
        repoLayout.setArtifactPathPattern("\u0000");
        repoLayout.setDescriptorPathPattern("\u0000");
        ConfigValidationService configValidationService = new ConfigValidationService();
        configValidationService.validate(repoLayout);
    }

    @Test(expectedExceptions = IllegalStateException.class, expectedExceptionsMessageRegExp = ".* Unicode is not " +
            "supported")
    public void testP2ConfigurationListOfUrlsWithUnicode() {
        P2Configuration configuration = new P2Configuration();
        configuration.setUrls(List.of("Valid", "alsoValid", "\u0000"));
        ConfigValidationService configValidationService = new ConfigValidationService();
        configValidationService.validate(configuration);
    }
}
