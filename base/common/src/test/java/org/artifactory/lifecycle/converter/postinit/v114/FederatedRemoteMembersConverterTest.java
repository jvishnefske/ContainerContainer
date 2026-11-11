package org.artifactory.lifecycle.converter.postinit.v114;

import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.federated.FederatedMember;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.version.CompoundVersionDetails;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.artifactory.repo.federated.FederatedMember.BASE_URL_PLACEHOLDER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FederatedRemoteMembersConverterTest extends ArtifactoryHomeBoundJUnitTest {

    @AfterAll
    public static void tearDown() {
        ArtifactoryHome.unbind();
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    public void testConvert() {
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        ArtifactoryContextThreadBinder.bind(context);
        when(context.beanForType(SecurityService.class)).thenReturn(mock(SecurityService.class));
        RepositoryConfigService repositoryConfigService = mock(RepositoryConfigService.class);
        when(context.beanForType(RepositoryConfigService.class)).thenReturn(repositoryConfigService);

        // given
        FederatedRepoConfig fedRepoConfig = FederatedRepoConfig.create("fed-repo", RepoType.Generic);
        FederatedMember localMember = new FederatedMember();
        localMember.setUrl(BASE_URL_PLACEHOLDER + "/" + fedRepoConfig.getKey());
        FederatedMember remoteMember1 = new FederatedMember();
        remoteMember1.setUrl("remote-url/remote-repo-1/");
        FederatedMember remoteMember2 = new FederatedMember();
        remoteMember2.setUrl("remote-url/remote-repo-2/");
        fedRepoConfig.getRepoTypeConfig().setFederatedMembers(List.of(localMember, remoteMember1, remoteMember2));
        when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.FEDERATED)).thenReturn(
                List.of(fedRepoConfig));

        // when
        CompoundVersionDetails source = mock(CompoundVersionDetails.class);
        FederatedRemoteMembersConverter converter = new FederatedRemoteMembersConverter(null);
        converter.convert(source, source);
        List<FederatedMember> federatedMembers = fedRepoConfig.getRepoTypeConfig().getFederatedMembers();
        assertEquals(3, federatedMembers.size());
        federatedMembers.forEach(member -> assertFalse(member.getUrlOrTemplate().endsWith("/")));
    }
}