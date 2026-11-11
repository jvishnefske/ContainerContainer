package org.artifactory.maven.index;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.config.CentralConfigDescriptorImpl;
import org.artifactory.descriptor.index.IndexerDescriptor;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RealRepoDescriptor;
import org.artifactory.repo.RealRepo;
import org.artifactory.repo.db.DbLocalRepo;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.virtual.VirtualRepo;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.ReflectionUtils;
import org.testng.annotations.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import static org.artifactory.repo.RepoDetailsType.FEDERATED;
import static org.artifactory.repo.RepoDetailsType.VIRTUAL;
import static org.testng.Assert.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertTrue;

/**
 * @author Alexei Vainshtein
 */
public class MavenIndexerServiceImplTest extends ArtifactoryHomeBoundTest {

    @Test
    public void testCalcSpecificReposForIndexing() {
        MavenIndexerServiceImpl indexerService = new MavenIndexerServiceImpl();
        InternalRepositoryService repositoryService = Mockito.mock(InternalRepositoryService.class);
        CentralConfigService centralConfigService = Mockito.mock(CentralConfigService.class);
        CentralConfigDescriptor configDescriptor = Mockito.mock(CentralConfigDescriptor.class);
        Mockito.when(centralConfigService.getDescriptor()).thenReturn(configDescriptor);
        indexerService.setRepositoryService(repositoryService);
        ReflectionTestUtils.setField(indexerService, "centralConfig", centralConfigService);
        Mockito.when(repositoryService.getLocalRepoConfigKeysExcludingInfoRepos()).thenReturn(List.of("local"));
        Mockito.when(repositoryService.getRepoConfigKeysByType(Set.of(VIRTUAL, FEDERATED)))
                .thenReturn(Sets.newHashSet("test"));
        Set<String> repos = indexerService.calcSpecificReposForIndexing(List.of("test"));
        assertEquals(repos.size(), 1);
    }

    @Test
    public void testSkipIndexingForNonExistingRepos() {
        MavenIndexerServiceImpl indexerService = Mockito.spy(new MavenIndexerServiceImpl());
        // mock configDescriptor
        Field centralConfigField = ReflectionUtils.findField(MavenIndexerServiceImpl.class, "centralConfig");
        assertNotNull(centralConfigField);
        ReflectionUtils.makeAccessible(centralConfigField);
        CentralConfigService centralConfigService = Mockito.mock(CentralConfigService.class);
        ReflectionUtils.setField(centralConfigField, indexerService, centralConfigService);
        CentralConfigDescriptorImpl centralConfigDescriptor = new CentralConfigDescriptorImpl();
        IndexerDescriptor mavenIndexer = new IndexerDescriptor();
        SortedSet<String> includedRepositories = new TreeSet<>();
        includedRepositories.add("does-not-exist-repo");
        mavenIndexer.setIncludedRepositories(includedRepositories);
        centralConfigDescriptor.setIndexer(mavenIndexer);
        Mockito.when(centralConfigService.getDescriptor()).thenReturn(centralConfigDescriptor);

        // mock repositoryService
        InternalRepositoryService repositoryService = Mockito.mock(InternalRepositoryService.class);
        indexerService.setRepositoryService(repositoryService);
        LocalRepoDescriptor localRepoDescriptor = new LocalRepoDescriptor();
        localRepoDescriptor.setKey("exists-repo");
        // TODO: 08/03/2022 replace with config service? layout comes from there.. 
        DbLocalRepo realRepo =
                new DbLocalRepo(localRepoDescriptor, localRepoDescriptor.toRepositoryConfig(), repositoryService,
                        null, null);
        ArrayList<RealRepo> realRepos = new ArrayList<>();
        realRepos.add(realRepo);
        Mockito.when(repositoryService.getLocalFederatedAndRemoteRepositories()).thenReturn(realRepos);

        // when
        indexerService.index(new MavenIndexerRunSettings(true, true, null));

        // then
        ArgumentCaptor<List> listArgumentCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Set> setArgumentCaptor = ArgumentCaptor.forClass(Set.class);
        Mockito.verify(indexerService)
                .mergeVirtualRepoIndexes(listArgumentCaptor.capture(), setArgumentCaptor.capture());
        assertTrue(listArgumentCaptor.getValue().isEmpty());
    }

    @Test
    public void index_withValidSettings_shouldIndexRepositories() {
        MavenIndexerServiceImpl indexerService = Mockito.spy(new MavenIndexerServiceImpl());
        CentralConfigService centralConfigService = Mockito.mock(CentralConfigService.class);
        CentralConfigDescriptor configDescriptor = Mockito.mock(CentralConfigDescriptor.class);
        IndexerDescriptor indexerDescriptor = Mockito.mock(IndexerDescriptor.class);
        Mockito.when(centralConfigService.getDescriptor()).thenReturn(configDescriptor);
        Mockito.when(configDescriptor.getIndexer()).thenReturn(indexerDescriptor);
        Mockito.when(indexerDescriptor.isEnabled()).thenReturn(true);
        indexerService.setRepositoryService(Mockito.mock(InternalRepositoryService.class));
        ReflectionTestUtils.setField(indexerService, "centralConfig", centralConfigService);

        MavenIndexerRunSettings settings = new MavenIndexerRunSettings(false, false, null);
        indexerService.index(settings);

        Mockito.verify(indexerService, Mockito.times(1)).mergeVirtualRepoIndexes(Mockito.anyList(), Mockito.anySet());
    }

    @Test
    public void index_withDisabledIndexer_shouldDoNothing() {
        MavenIndexerServiceImpl indexerService = Mockito.spy(new MavenIndexerServiceImpl());
        CentralConfigService centralConfigService = Mockito.mock(CentralConfigService.class);
        CentralConfigDescriptor configDescriptor = Mockito.mock(CentralConfigDescriptor.class);
        IndexerDescriptor indexerDescriptor = Mockito.mock(IndexerDescriptor.class);
        Mockito.when(centralConfigService.getDescriptor()).thenReturn(configDescriptor);
        Mockito.when(configDescriptor.getIndexer()).thenReturn(indexerDescriptor);
        Mockito.when(indexerDescriptor.isEnabled()).thenReturn(false);
        indexerService.setRepositoryService(Mockito.mock(InternalRepositoryService.class));
        ReflectionTestUtils.setField(indexerService, "centralConfig", centralConfigService);

        MavenIndexerRunSettings settings = new MavenIndexerRunSettings(false, false, null);
        indexerService.index(settings);

        Mockito.verify(indexerService, Mockito.never()).mergeVirtualRepoIndexes(Mockito.anyList(), Mockito.anySet());
    }

    @Test
    public void index_withEmptyRepoKeys_shouldIndexAllIncludedRepositories() {
        MavenIndexerServiceImpl indexerService = Mockito.spy(new MavenIndexerServiceImpl());
        CentralConfigService centralConfigService = Mockito.mock(CentralConfigService.class);
        CentralConfigDescriptor configDescriptor = Mockito.mock(CentralConfigDescriptor.class);
        IndexerDescriptor indexerDescriptor = Mockito.mock(IndexerDescriptor.class);
        Mockito.when(centralConfigService.getDescriptor()).thenReturn(configDescriptor);
        Mockito.when(configDescriptor.getIndexer()).thenReturn(indexerDescriptor);
        Mockito.when(indexerDescriptor.isEnabled()).thenReturn(true);
        SortedSet<String> sortedSet = new TreeSet<>();
        sortedSet.add("repo1");
        sortedSet.add("repo2");
        Mockito.when(indexerDescriptor.getIncludedRepositories()).thenReturn(sortedSet);
        indexerService.setRepositoryService(Mockito.mock(InternalRepositoryService.class));
        ReflectionTestUtils.setField(indexerService, "centralConfig", centralConfigService);

        MavenIndexerRunSettings settings = new MavenIndexerRunSettings(false, false, null);
        indexerService.index(settings);

        Mockito.verify(indexerService, Mockito.times(1))
                .mergeVirtualRepoIndexes(Mockito.anyList(), Mockito.eq(Sets.newHashSet("repo1", "repo2")));
    }

    @Test
    public void index_withSpecificRepoKeys_shouldIndexOnlySpecifiedRepositories() {
        MavenIndexerServiceImpl indexerService = Mockito.spy(new MavenIndexerServiceImpl());
        CentralConfigService centralConfigService = Mockito.mock(CentralConfigService.class);
        CentralConfigDescriptor configDescriptor = Mockito.mock(CentralConfigDescriptor.class);
        IndexerDescriptor indexerDescriptor = Mockito.mock(IndexerDescriptor.class);
        InternalRepositoryService repositoryService = Mockito.mock(InternalRepositoryService.class);

        Mockito.when(centralConfigService.getDescriptor()).thenReturn(configDescriptor);
        Mockito.when(configDescriptor.getIndexer()).thenReturn(indexerDescriptor);
        Mockito.when(indexerDescriptor.isEnabled()).thenReturn(true);
        Mockito.when(repositoryService.getRepoConfigKeysByType(Mockito.anySet()))
                .thenReturn(Sets.newHashSet("repo1"));

        indexerService.setRepositoryService(Mockito.mock(InternalRepositoryService.class));
        indexerService.setRepositoryService(repositoryService);
        ReflectionTestUtils.setField(indexerService, "centralConfig", centralConfigService);

        MavenIndexerRunSettings settings = new MavenIndexerRunSettings(false, false, Lists.newArrayList("repo1"));
        indexerService.index(settings);

        Mockito.verify(indexerService, Mockito.times(1))
                .mergeVirtualRepoIndexes(Mockito.anyList(), Mockito.eq(Sets.newHashSet("repo1")));
    }

    @Test
    public void index_withNullIncludedRepositories_shouldDoNothing() {
        MavenIndexerServiceImpl indexerService = Mockito.spy(new MavenIndexerServiceImpl());
        CentralConfigService centralConfigService = Mockito.mock(CentralConfigService.class);
        CentralConfigDescriptor configDescriptor = Mockito.mock(CentralConfigDescriptor.class);
        IndexerDescriptor indexerDescriptor = Mockito.mock(IndexerDescriptor.class);

        Mockito.when(centralConfigService.getDescriptor()).thenReturn(configDescriptor);
        Mockito.when(configDescriptor.getIndexer()).thenReturn(indexerDescriptor);
        Mockito.when(indexerDescriptor.isEnabled()).thenReturn(true);
        Mockito.when(indexerDescriptor.getIncludedRepositories()).thenReturn(null);

        indexerService.setRepositoryService(Mockito.mock(InternalRepositoryService.class));
        ReflectionTestUtils.setField(indexerService, "centralConfig", centralConfigService);

        MavenIndexerRunSettings settings = new MavenIndexerRunSettings(false, false, null);
        indexerService.index(settings);

        Mockito.verify(indexerService, Mockito.never()).mergeVirtualRepoIndexes(Mockito.anyList(), Mockito.anySet());
    }

    @Test
    public void calcSpecificReposForIndexing_withEmptyRepoKeys_shouldReturnEmptySet() {
        MavenIndexerServiceImpl indexerService = new MavenIndexerServiceImpl();
        InternalRepositoryService repositoryService = Mockito.mock(InternalRepositoryService.class);
        indexerService.setRepositoryService(repositoryService);

        Mockito.when(repositoryService.getRepoConfigKeysByType(Mockito.anySet())).thenReturn(new HashSet<>());
        Mockito.when(repositoryService.getLocalRepoConfigKeysExcludingInfoRepos()).thenReturn(List.of());

        Set<String> result = indexerService.calcSpecificReposForIndexing(new ArrayList<>());

        assertTrue(result.isEmpty());
    }

    @Test
    public void getNonVirtualRepositoriesToIndex_withNullIncludedRepositories_shouldReturnAllRepos() {
        MavenIndexerServiceImpl indexerService = new MavenIndexerServiceImpl();
        InternalRepositoryService repositoryService = Mockito.mock(InternalRepositoryService.class);
        List<RealRepo> allRepos =List.of(Mockito.mock(RealRepo.class), Mockito.mock(RealRepo.class));

        Mockito.when(repositoryService.getLocalFederatedAndRemoteRepositories()).thenReturn(allRepos);

        indexerService.setRepositoryService(repositoryService);

        List<RealRepo> result = indexerService.getNonVirtualRepositoriesToIndex(null);

        assertEquals(result, allRepos);
    }

    @Test
    public void getNonVirtualRepositoriesToIndex_withIncludedRepositories_shouldFilterRepos() {
        MavenIndexerServiceImpl indexerService = new MavenIndexerServiceImpl();
        InternalRepositoryService repositoryService = Mockito.mock(InternalRepositoryService.class);
        RealRepo repo1 = Mockito.mock(RealRepo.class);
        RealRepo repo2 = Mockito.mock(RealRepo.class);
        RealRepoDescriptor repoDescriptor1 = Mockito.mock(RealRepoDescriptor.class);
        RealRepoDescriptor repoDescriptor2 = Mockito.mock(RealRepoDescriptor.class);

        Mockito.when(repo1.getDescriptor()).thenReturn(repoDescriptor1);
        Mockito.when(repo2.getDescriptor()).thenReturn(repoDescriptor2);
        Mockito.when(repoDescriptor1.getKey()).thenReturn("repo1");
        Mockito.when(repoDescriptor2.getKey()).thenReturn("repo2");
        List<RealRepo> allRepos = List.of(repo1, repo2);

        Mockito.when(repositoryService.getLocalFederatedAndRemoteRepositories()).thenReturn(allRepos);

        indexerService.setRepositoryService(repositoryService);

        List<RealRepo> result = indexerService.getNonVirtualRepositoriesToIndex(Set.of("repo1"));

        assertEquals(result, List.of(repo1));
    }

    @Test
    public void getVirtualRepos_withValidInputs_shouldReturnFilteredVirtualRepos() {
        MavenIndexerServiceImpl indexerService = new MavenIndexerServiceImpl();
        InternalRepositoryService repositoryService = Mockito.mock(InternalRepositoryService.class);
        VirtualRepo virtualRepo1 = Mockito.mock(VirtualRepo.class);
        VirtualRepo virtualRepo2 = Mockito.mock(VirtualRepo.class);

        Mockito.when(virtualRepo1.getKey()).thenReturn("virtualRepo1");
        Mockito.when(virtualRepo2.getKey()).thenReturn("virtualRepo2");

        List<VirtualRepo> allVirtualRepos = List.of(virtualRepo1, virtualRepo2);
        Mockito.when(repositoryService.getVirtualRepositories()).thenReturn(allVirtualRepos);

        indexerService.setRepositoryService(repositoryService);

        Set<String> alreadyIndexedRepositories = Set.of("repo1");
        Set<String> includedRepositories = Set.of("virtualRepo1", "virtualRepo2");


        List<VirtualRepo> result = indexerService.getVirtualRepos(alreadyIndexedRepositories, includedRepositories);
        assertEquals(2, result.size());
        assertTrue(result.contains(virtualRepo1));
        assertTrue(result.contains(virtualRepo2));

        result = indexerService.getVirtualRepos(Set.of("virtualRepo1"), includedRepositories);
        assertEquals(1, result.size());
        assertTrue(result.contains(virtualRepo2));
    }



}