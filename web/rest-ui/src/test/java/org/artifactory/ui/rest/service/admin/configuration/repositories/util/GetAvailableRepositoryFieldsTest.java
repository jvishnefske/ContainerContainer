package org.artifactory.ui.rest.service.admin.configuration.repositories.util;

import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

import static org.artifactory.common.ConstantValues.listOfReposForcedCdnRedirectUrl;
import static org.artifactory.common.ConstantValues.listOfReposAllowedSendRedirectUrl;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * @author Shay Bagants
 */
public class GetAvailableRepositoryFieldsTest extends ArtifactoryHomeBoundTest {

    private GetAvailableRepositoryFields getAvailableRepositoryFields;
    private AutoCloseable mocks;

    @BeforeMethod
    private void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        getAvailableRepositoryFields = new GetAvailableRepositoryFields();
    }

    @AfterMethod
    public void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    public void getCdnRepositories() {
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(listOfReposAllowedSendRedirectUrl.getPropertyName(), "maven, Gradle,Docker, npm ");
        List<String> cdnRepositories = getAvailableRepositoryFields.getCdnRepositories();
        verifyList(cdnRepositories);
    }

    @Test
    public void getForcedCdnRepositories() {
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(listOfReposForcedCdnRedirectUrl.getPropertyName(), "maven, Gradle,Docker, npm ");
        List<String> cdnRepositories = getAvailableRepositoryFields.getForcedCdnRepositories();
        verifyList(cdnRepositories);
    }

    private void verifyList(List<String> repositories) {
        assertEquals(repositories.size(), 4);
        assertTrue(repositories.contains("maven"));
        assertTrue(repositories.contains("gradle"));
        assertTrue(repositories.contains("docker"));
        assertTrue(repositories.contains("npm"));
    }
}