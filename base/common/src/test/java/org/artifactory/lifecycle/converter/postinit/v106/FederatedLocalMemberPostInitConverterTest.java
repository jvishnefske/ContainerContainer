package org.artifactory.lifecycle.converter.postinit.v106;

import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.config.CentralConfigDescriptorImpl;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.lifecycle.converter.postinit.v107.FederatedLocalMemberPostInitConverter;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.federated.FederatedMember;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.version.CompoundVersionDetails;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;

import static org.artifactory.repo.federated.FederatedMember.BASE_URL_PLACEHOLDER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Tamar Rosen
 */
public class FederatedLocalMemberPostInitConverterTest extends ArtifactoryHomeBoundTest {

    private RepositoryConfigService repositoryConfigService;
    private CentralConfigDescriptorImpl configDescriptor;
    private FederatedLocalMemberPostInitConverter converter;

    @BeforeMethod
    public void setUp() {
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        ArtifactoryContextThreadBinder.bind(context);
        when(context.beanForType(SecurityService.class)).thenReturn(mock(SecurityService.class));
        repositoryConfigService = mock(RepositoryConfigService.class);
        when(context.beanForType(RepositoryConfigService.class)).thenReturn(repositoryConfigService);
        CentralConfigService configService = mock(CentralConfigService.class);
        configDescriptor = new CentralConfigDescriptorImpl();
        when(configService.getDescriptor()).thenReturn(configDescriptor);
        when(context.beanForType(CentralConfigService.class)).thenReturn(configService);
        converter = new FederatedLocalMemberPostInitConverter(null, null);
    }

    @AfterMethod
    public void tearDown() {
        ArtifactoryHome.unbind();
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test(dataProvider = "baseUrlProvider")
    public void testConvert(String baseUrl, String fedBaseUrl) {
        configDescriptor.setUrlBase(baseUrl);
        configDescriptor.setFederatedRepoUrlBase(fedBaseUrl);

        FederatedRepoConfig fedRepoConfig = FederatedRepoConfig.create("fed-repo", RepoType.Generic);
        FederatedMember localMember = new FederatedMember();
        localMember.setUrl(configDescriptor.getFederatedRepoUrlBaseOrDefault() + "/" + fedRepoConfig.getKey());
        fedRepoConfig.getRepoTypeConfig().getFederatedMembers().add(localMember);
        when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.FEDERATED)).thenReturn(
                List.of(fedRepoConfig));

        // when
        CompoundVersionDetails source = mock(CompoundVersionDetails.class);
        converter.convert(source, source);
        List<FederatedMember> federatedMembers = fedRepoConfig.getRepoTypeConfig().getFederatedMembers();
        assertEquals(1, federatedMembers.size());
        FederatedMember convertedLocalMember = federatedMembers.get(0);
        assertEquals(BASE_URL_PLACEHOLDER + "/" + fedRepoConfig.getKey(), convertedLocalMember.getUrlOrTemplate());
    }

    @Test
    public void testConvertIsIdempotent() {
        configDescriptor.setUrlBase("http://base-url:8080/artifactory");

        FederatedRepoConfig fedRepoConfig = FederatedRepoConfig.create("fed-repo", RepoType.Generic);
        FederatedMember localMember = new FederatedMember();
        localMember.setUrl(configDescriptor.getFederatedRepoUrlBaseOrDefault() + "/" + fedRepoConfig.getKey());
        fedRepoConfig.getRepoTypeConfig().getFederatedMembers().add(localMember);
        when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.FEDERATED)).thenReturn(
                List.of(fedRepoConfig));

        CompoundVersionDetails source = mock(CompoundVersionDetails.class);
        converter.convert(source, source);
        converter.convert(source, source);

        List<FederatedMember> federatedMembers = fedRepoConfig.getRepoTypeConfig().getFederatedMembers();
        assertEquals(1, federatedMembers.size());
        assertEquals(BASE_URL_PLACEHOLDER + "/" + fedRepoConfig.getKey(), federatedMembers.get(0).getUrlOrTemplate());
    }

    @DataProvider
    public static Object[][] baseUrlProvider() {
        return new Object[][] {
                {"http://base-url:8080/artifactory", null},
                {null, "http://fed-base-url:8080/artifactory"},
                {"http://base-url:8080/artifactory", "http://fed-base-url:8080/artifactory"}
        };
    }
}