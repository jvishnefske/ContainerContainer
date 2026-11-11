package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.fetch;

import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.jfrog.sysconf.SysConfig;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.support.SupportAddon;
import org.artifactory.aol.AolService;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.descriptor.repo.HttpRepoDescriptor;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RemoteRepoDescriptor;
import org.artifactory.descriptor.repo.RepoBaseDescriptor;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.LocalConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.ReleaseBundleRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.system.SystemRepos;
import org.artifactory.rest.common.model.RestModel;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.repo.RepositoryNode;
import org.glassfish.jersey.internal.guava.Lists;
import org.jfrog.common.StreamSupportUtils;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.artifactory.repo.system.SystemRepos.LOG_SHIPPING_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.TRASH_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.USAGE_REPO_KEY;
import static org.artifactory.util.bundles.BundleConfigurationUtil.DEFAULT_RBV2_REPO;
import static org.artifactory.util.evidence.EvidenceSystemYamlConstants.EVIDENCE_NAVIGATION_ENABLED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Omri Ziv
 */
public abstract class RootFetchStrategyTest {

    protected final static String PROJECT_A = "projectA";
    protected final static String DEFAULT_PROJECT = ProjectsService.DEFAULT_PROJECT;

    @Mock
    AuthorizationService authorizationService;
    @Mock
    SecurityService securityService;
    @Mock
    RepositoryService repoService;
    @Mock
    RepositoryConfigService repositoryConfigService;
    @Mock
    AddonsManager addonsManager;
    @Mock
    SupportAddon supportAddon;
    @Mock
    AolService aolService;
    @Mock
    ProjectsService projectsService;
    @Mock
    private ArtifactoryContext context;
    @Mock
    ArtifactoryHome artifactoryHome;
    @Mock
    ArtifactorySystemProperties artifactorySystemProperties;
    private AutoCloseable mocks;

    @BeforeMethod
    public void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        ArtifactoryHome.bind(artifactoryHome);

        SysConfig sysConfig = mock(SysConfig.class);
        when(sysConfig.getBoolean(EVIDENCE_NAVIGATION_ENABLED, false)).thenReturn(false);
        when(artifactoryHome.getSysConfig()).thenReturn(sysConfig);

        when(artifactoryHome.getArtifactoryProperties()).thenReturn(artifactorySystemProperties);
        when(artifactorySystemProperties.getLongProperty(any(ConstantValues.class)))
                .thenAnswer(invocationOnMock -> {
                    ConstantValues constantValue = invocationOnMock.getArgument(0);
                    return Long.parseLong(constantValue.getDefValue().trim());
                });
        when(artifactorySystemProperties.getProperty(any(ConstantValues.class)))
                .thenAnswer(invocationOnMock -> {
                    ConstantValues constantValue = invocationOnMock.getArgument(0);
                    if (constantValue == ConstantValues.orderTreeBrowserRepositoriesByType) {
                        return "local,remote,virtual";
                    }
                    return constantValue.getDefValue().trim();
                });
        ArtifactoryContextThreadBinder.bind(context);
        when(context.getRepositoryService())
                .thenReturn(repoService);
        when(context.getRepositoryConfigService())
                .thenReturn(repositoryConfigService);
        when(context.getAuthorizationService())
                .thenReturn(authorizationService);
        when(context.beanForType(SecurityService.class))
                .thenReturn(securityService);
        when(context.beanForType(AddonsManager.class))
                .thenReturn(addonsManager);
        when(context.beanForType(RepositoryService.class))
                .thenReturn(repoService);
        when(context.beanForType(AolService.class))
                .thenReturn(aolService);
        when(context.beanForType(ProjectsService.class))
                .thenReturn(projectsService);
        when(projectsService.createVirtualReposVisibilityFilter(any()))
                .thenReturn(repo -> true);
        when(aolService.isAol())
                .thenReturn(false);
        when(addonsManager.addonByType(SupportAddon.class))
                .thenReturn(supportAddon);
        when(supportAddon.isSupportAddonEnabled())
                .thenReturn(false);
        when(authorizationService.userHasPermissionsOnRepositoryRoot(any()))
                .thenReturn(true);
        when(authorizationService.userHasPermissionsOnRepositoryRoot(any(), any(), any()))
                .thenReturn(true);
        when(securityService.isAdmin())
                .thenReturn(true);
        when(authorizationService.canAccessTrashcan())
                .thenReturn(true);
    }

    @AfterMethod
    public void unbind() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        ArtifactoryHome.unbind();
        mocks.close();
    }

    protected void createMocks() {
        List<LocalRepoDescriptor> mavenLocalRepoDescriptor = createLocalRepoDescriptors(RepoType.Maven, 10);
        List<LocalRepoDescriptor> dockerLocalRepoDescriptor = createLocalRepoDescriptors(RepoType.Docker, 5);
        List<LocalRepoDescriptor> npmLocalRepoDescriptor = createLocalRepoDescriptors(RepoType.Npm, 7);
        LocalRepoDescriptor consumptionRepo = new LocalRepoDescriptor();
        consumptionRepo.setType(RepoType.Generic);
        consumptionRepo.setKey(USAGE_REPO_KEY);
        LocalRepoDescriptor defaultReleaseBundleLocalRepoDescriptor =
                createLocalRepoDescriptor(DEFAULT_RBV2_REPO, RepoType.ReleaseBundles);
        LocalRepoDescriptor releaseBundleLocalRepoDescriptor =
                createLocalRepoDescriptor("project-release-bundles-v2", RepoType.ReleaseBundles);
        VirtualRepoDescriptor virtualRepoDescriptor = new VirtualRepoDescriptor();
        virtualRepoDescriptor.setKey("npm-virtual");
        virtualRepoDescriptor.setType(RepoType.Npm);
        RemoteRepoDescriptor remoteRepoDescriptor = new HttpRepoDescriptor();
        remoteRepoDescriptor.setKey("npm-remote");
        remoteRepoDescriptor.setType(RepoType.Npm);
        Map<RepoType, Integer> repoTypeRepoDescriptorMap = Maps.newTreeMap(new RepoType.RepoNameComparator());
        repoTypeRepoDescriptorMap.put(RepoType.Maven, mavenLocalRepoDescriptor.size());
        repoTypeRepoDescriptorMap.put(RepoType.Docker, dockerLocalRepoDescriptor.size());
        repoTypeRepoDescriptorMap.put(RepoType.Npm, npmLocalRepoDescriptor.size());
        repoTypeRepoDescriptorMap.put(RepoType.ReleaseBundles, 2);
        List<LocalRepoDescriptor> localRepoDescriptor = Lists.newArrayList(Iterables.concat(mavenLocalRepoDescriptor,
                dockerLocalRepoDescriptor, npmLocalRepoDescriptor, List.of(consumptionRepo),
                List.of(defaultReleaseBundleLocalRepoDescriptor, releaseBundleLocalRepoDescriptor)));
        List<LocalConfig> localRepoConfigs = localRepoDescriptor.stream()
                .map(LocalRepoDescriptor::toRepositoryConfig)
                .map(config -> config)
                .toList();
        List<LocalConfig> mavenLocalRepoConfigs = mavenLocalRepoDescriptor.stream()
                .map(LocalRepoDescriptor::toRepositoryConfig)
                .toList();
        List<LocalConfig> dockerLocalRepoConfigs = dockerLocalRepoDescriptor.stream()
                .map(LocalRepoDescriptor::toRepositoryConfig)
                .toList();
        List<LocalConfig> npmLocalRepoConfigs = npmLocalRepoDescriptor.stream()
                .map(LocalRepoDescriptor::toRepositoryConfig)
                .toList();
        List<RepoBaseDescriptor> npmDescriptors = Lists.newArrayList(npmLocalRepoDescriptor);
        npmDescriptors.add(remoteRepoDescriptor);
        npmDescriptors.add(virtualRepoDescriptor);
        List<RepoConfig> npmRepoConfigs = npmDescriptors.stream()
                .map(RepoBaseDescriptor::toRepositoryConfig)
                .toList();

        Map<Character, List<String>> characterListMap =
                createCharacterListMap(Lists.newArrayList(localRepoDescriptor));

        LocalRepoDescriptor trashDescriptor = new LocalRepoDescriptor();
        trashDescriptor.setKey("trash");
        Function defaultProjectRepoFilter = mock(Function.class);
        when(repoService.createProjectReposFilter(DEFAULT_PROJECT)).thenReturn(defaultProjectRepoFilter);
        when(repositoryConfigService.getRepoConfigsByProjectAndType(any(), eq(Set.of(RepoDetailsType.FEDERATED))))
                .thenReturn(Collections.singletonList(FederatedRepoConfig.create("gen-fed", RepoType.Generic)));
        when(repositoryConfigService.getRepoConfigKeysByType(Set.of(RepoDetailsType.RELEASE_BUNDLE)))
                .thenReturn(Set.of("release-bundles"));
        when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.RELEASE_BUNDLE))
                .thenReturn(List.of(ReleaseBundleRepoConfig.create("release-bundles")));
        when(repoService.getRepoConfigsByPackageTypeAndProject(RepoType.Maven, DEFAULT_PROJECT))
                .thenReturn(Lists.newArrayList(mavenLocalRepoConfigs));
        when(repoService.getRepoConfigsByPackageTypeAndProject(RepoType.Docker, DEFAULT_PROJECT))
                .thenReturn(Lists.newArrayList(dockerLocalRepoConfigs));
        when(repoService.getRepoConfigsByPackageTypeAndProject(RepoType.Npm, DEFAULT_PROJECT))
                .thenReturn(Lists.newArrayList(npmRepoConfigs));
        when(repoService.getRepoDescriptorByPackageTypeCountAndProject(DEFAULT_PROJECT))
                .thenReturn(repoTypeRepoDescriptorMap);
        when(repoService.getLocalRepoConfigsIncludingInfoReposByProject(DEFAULT_PROJECT))
                .thenReturn(localRepoConfigs);
        when(repositoryConfigService.getRepoConfigsByProjectAndType(any(),
                eq(Set.of(RepoDetailsType.VIRTUAL))))
                .thenReturn(Collections.singletonList(VirtualRepoConfig.create("npm-virtual", RepoType.Npm)));
        when(repositoryConfigService.getRepoConfigsByProjectAndType(any(), eq(Set.of(RepoDetailsType.REMOTE))))
                .thenReturn(Collections.singletonList(RemoteRepoConfig.create("npm-remote", RepoType.Npm)));
        when(repoService.getAllRepoKeysByFirstCharMapAndProject(DEFAULT_PROJECT))
                .thenReturn(characterListMap);
        when(repoService.repoDescriptorByKey(anyString()))
                .thenAnswer(invocationOnMock -> {
                    String repoKey = invocationOnMock.getArgument(0);
                    return StreamSupportUtils.stream(localRepoDescriptor)
                            .filter(repoDescriptor -> repoDescriptor.getKey().equals(repoKey))
                            .findFirst().orElse(null);
                });
        when(repoService.localOrFederatedRepoDescriptorByKey(TRASH_REPO_KEY))
                .thenReturn(trashDescriptor);
        when(repoService.hasChildren(any()))
                .thenReturn(false);

        when(repoService.getRepoConfigsByPackageTypeAndProject(RepoType.Maven, PROJECT_A))
                .thenReturn(List.of());
        when(repoService.getRepoConfigsByPackageTypeAndProject(RepoType.Docker, PROJECT_A))
                .thenReturn(List.of());
        when(repoService.getRepoConfigsByPackageTypeAndProject(RepoType.Npm, PROJECT_A))
                .thenReturn(Lists.newArrayList(npmRepoConfigs));
        Map<RepoType, Integer> repoTypeRepoDescriptorProjectMap = Maps.newTreeMap(new RepoType.RepoNameComparator());
        repoTypeRepoDescriptorProjectMap.put(RepoType.Npm, npmLocalRepoDescriptor.size());
        when(repoService.getRepoDescriptorByPackageTypeCountAndProject(PROJECT_A))
                .thenReturn(repoTypeRepoDescriptorProjectMap);
        when(repoService.getLocalRepoConfigsIncludingInfoReposByProject(PROJECT_A))
                .thenReturn(npmLocalRepoConfigs);
        when(repositoryConfigService.getRepoConfigsByProjectAndType(any(), eq(Set.of(RepoDetailsType.REMOTE))))
                .thenReturn(Collections.singletonList(RemoteRepoConfig.create("npm-remote", RepoType.Npm)));
        Map<Character, List<String>> projectCharacterListMap =
                createCharacterListMap(Lists.newArrayList(npmLocalRepoDescriptor));
        when(repoService.getAllRepoKeysByFirstCharMapAndProject(PROJECT_A))
                .thenReturn(projectCharacterListMap);
    }

    protected void createMocksWithLocalAndVirtual() {
        List<LocalRepoDescriptor> mavenLocalRepoDescriptor = createLocalRepoDescriptors(RepoType.Maven, 10);
        List<LocalRepoDescriptor> dockerLocalRepoDescriptor = createLocalRepoDescriptors(RepoType.Docker, 5);
        List<LocalRepoDescriptor> npmLocalRepoDescriptor = createLocalRepoDescriptors(RepoType.Npm, 7);
        List<LocalConfig> npmLocalRepoConfigs = createLocalRepoConfigs(RepoType.Npm, 7);
        LocalRepoDescriptor consumptionRepo = new LocalRepoDescriptor();
        consumptionRepo.setType(RepoType.Generic);
        consumptionRepo.setKey(USAGE_REPO_KEY);
        LocalRepoDescriptor logShipping = new LocalRepoDescriptor();
        logShipping.setType(RepoType.Generic);
        logShipping.setKey(LOG_SHIPPING_REPO_KEY);
        List<VirtualRepoDescriptor> mavenVirtualRepoDescriptor = createVirtualRepoDescriptors(RepoType.Maven, 3);
        List<VirtualRepoDescriptor> dockerVirtualRepoDescriptor = createVirtualRepoDescriptors(RepoType.Docker, 4);
        List<VirtualRepoDescriptor> npmVirtualRepoDescriptor = createVirtualRepoDescriptors(RepoType.Npm, 1);
        List<RepoConfig> npmVirtualRepoConfigs = npmVirtualRepoDescriptor.stream()
                .map(RepoBaseDescriptor::toRepositoryConfig)
                .toList();

        Map<RepoType, Integer> repoTypeRepoDescriptorMap = Maps.newTreeMap(new RepoType.RepoNameComparator());

        List<RepoBaseDescriptor> mavenRepoDescriptor = Lists.newArrayList(
                Iterables.concat(mavenLocalRepoDescriptor, mavenVirtualRepoDescriptor));
        List<RepoConfig> mavenRepoConfigs = mavenRepoDescriptor.stream()
                .map(RepoBaseDescriptor::toRepositoryConfig)
                .toList();
        List<RepoBaseDescriptor> dockerRepoDescriptor = Lists.newArrayList(Iterables.concat(dockerLocalRepoDescriptor,
                dockerVirtualRepoDescriptor));
        List<RepoConfig> dockerRepoConfigs = dockerRepoDescriptor.stream()
                .map(RepoBaseDescriptor::toRepositoryConfig)
                .toList();
        List<RepoBaseDescriptor> npmRepoDescriptor =
                Lists.newArrayList(Iterables.concat(npmLocalRepoDescriptor, npmVirtualRepoDescriptor));
        List<RepoConfig> npmRepoConfigs = npmRepoDescriptor.stream()
                .map(RepoBaseDescriptor::toRepositoryConfig)
                .toList();
        List<LocalRepoDescriptor> localRepoDescriptor = Lists.newArrayList(Iterables.concat(mavenLocalRepoDescriptor,
                dockerLocalRepoDescriptor, npmLocalRepoDescriptor, List.of(consumptionRepo)));
        List<LocalConfig> localRepoConfigs = localRepoDescriptor.stream()
                .map(LocalRepoDescriptor::toRepositoryConfig)
                .toList();
        List<VirtualRepoDescriptor> virtualRepoDescriptor =
                Lists.newArrayList(Iterables.concat(mavenVirtualRepoDescriptor,
                        dockerVirtualRepoDescriptor, npmVirtualRepoDescriptor));
        List<RepoConfig> virtualRepoConfigs = virtualRepoDescriptor.stream()
                .map(VirtualRepoDescriptor::toRepositoryConfig)
                .map(RepoConfig.class::cast)
                .toList();

        List<RepoDescriptor> allRepoDescriptor =
                Lists.newArrayList(Iterables.concat(localRepoDescriptor, virtualRepoDescriptor));
        Map<Character, List<String>> characterListMap =
                createCharacterListMap(allRepoDescriptor);
        repoTypeRepoDescriptorMap.put(RepoType.Maven, mavenRepoDescriptor.size());
        repoTypeRepoDescriptorMap.put(RepoType.Docker, dockerRepoDescriptor.size());
        repoTypeRepoDescriptorMap.put(RepoType.Npm, npmRepoDescriptor.size());
        LocalRepoDescriptor trashDescriptor = new LocalRepoDescriptor();
        trashDescriptor.setKey("trash");

        when(repoService.getRepoConfigsByPackageTypeAndProject(RepoType.Maven, DEFAULT_PROJECT))
                .thenReturn(mavenRepoConfigs);
        when(repoService.getRepoConfigsByPackageTypeAndProject(RepoType.Docker, DEFAULT_PROJECT))
                .thenReturn(dockerRepoConfigs);
        when(repoService.getRepoConfigsByPackageTypeAndProject(RepoType.Npm, DEFAULT_PROJECT))
                .thenReturn(npmRepoConfigs);
        when(repoService.getRepoDescriptorByPackageTypeCountAndProject(DEFAULT_PROJECT))
                .thenReturn(repoTypeRepoDescriptorMap);
        when(repoService.getLocalRepoConfigsIncludingInfoReposByProject(DEFAULT_PROJECT))
                .thenReturn(localRepoConfigs);
        when(repositoryConfigService.getRepoConfigsByProjectAndType(any(), eq(Set.of(RepoDetailsType.REMOTE))))
                .thenReturn(Collections.emptyList());

        Function defaultProjectFilter = mock(Function.class);
        Function projectAFilter = mock(Function.class);
        when(repoService.createProjectReposFilter(DEFAULT_PROJECT)).thenReturn(defaultProjectFilter);
        when(repoService.createProjectReposFilter(PROJECT_A)).thenReturn(projectAFilter);

        when(repositoryConfigService.getRepoConfigsByProjectAndType(eq(defaultProjectFilter),
                eq(Set.of(RepoDetailsType.VIRTUAL)))).thenReturn(virtualRepoConfigs);
        when(repoService.getAllRepoKeysByFirstCharMapAndProject(DEFAULT_PROJECT))
                .thenReturn(characterListMap);
        when(repoService.repoDescriptorByKey(anyString()))
                .thenAnswer(invocationOnMock -> {
                    String repoKey = invocationOnMock.getArgument(0);
                    return StreamSupportUtils.stream(allRepoDescriptor)
                            .filter(repoDescriptor -> repoDescriptor.getKey().equals(repoKey))
                            .findFirst().orElse(null);
                });
        when(repoService.localOrFederatedRepoDescriptorByKey(TRASH_REPO_KEY))
                .thenReturn(trashDescriptor);
        when(repoService.localOrFederatedRepoDescriptorByKey(USAGE_REPO_KEY))
                .thenReturn(consumptionRepo);
        when(repoService.localOrFederatedRepoDescriptorByKey(LOG_SHIPPING_REPO_KEY))
                .thenReturn(logShipping);
        when(repoService.hasChildren(any()))
                .thenReturn(false);

        when(repoService.getRepoConfigsByPackageTypeAndProject(RepoType.Maven, PROJECT_A))
                .thenReturn(List.of());
        when(repoService.getRepoConfigsByPackageTypeAndProject(RepoType.Docker, PROJECT_A))
                .thenReturn(List.of());
        when(repoService.getRepoConfigsByPackageTypeAndProject(RepoType.Npm, PROJECT_A))
                .thenReturn(npmRepoConfigs);
        Map<RepoType, Integer> repoTypeRepoDescriptorProjectMap = Maps.newTreeMap(new RepoType.RepoNameComparator());
        repoTypeRepoDescriptorProjectMap.put(RepoType.Npm, npmRepoDescriptor.size());
        when(repoService.getRepoDescriptorByPackageTypeCountAndProject(PROJECT_A))
                .thenReturn(repoTypeRepoDescriptorProjectMap);
        when(repoService.getLocalRepoConfigsIncludingInfoReposByProject(PROJECT_A))
                .thenReturn(npmLocalRepoConfigs);
        when(repositoryConfigService.getRepoConfigsByProjectAndType(eq(projectAFilter),
                eq(Set.of(RepoDetailsType.VIRTUAL)))).thenReturn(npmVirtualRepoConfigs);
        when(repositoryConfigService.getRepoConfigsByProjectAndType(any(), eq(Set.of(RepoDetailsType.REMOTE))))
                .thenReturn(Collections.emptyList());
        Map<Character, List<String>> projectCharacterListMap =
                createCharacterListMap(Lists.newArrayList(npmLocalRepoDescriptor));
        when(repoService.getAllRepoKeysByFirstCharMapAndProject(PROJECT_A))
                .thenReturn(projectCharacterListMap);
    }

    protected boolean testRepositoryNodeBefore(List<? extends RestModel> repositoryNodes, String first, String second) {
        List<String> list = StreamSupportUtils.stream(repositoryNodes)
                .filter(repositoryNode -> {
                    return ((RepositoryNode) repositoryNode).getText().equals(first)
                            || ((RepositoryNode) repositoryNode).getText().equals(second);
                })
                .map(repositoryNode -> ((RepositoryNode) repositoryNode).getText())
                .collect(Collectors.toList());
        return list.size() == 2 && list.get(0).equals(first) && list.get(1).equals(second);
    }

    private List<LocalRepoDescriptor> createLocalRepoDescriptors(RepoType repoType, int number) {
        return IntStream.range(1, number + 1)
                .mapToObj(index -> createLocalRepoDescriptor(createRepoKey(repoType, index), repoType))
                .collect(Collectors.toList());
    }

    private List<LocalConfig> createLocalRepoConfigs(RepoType repoType, int number) {
        return IntStream.range(1, number + 1)
                .mapToObj(index -> LocalRepoConfig.create(createRepoKey(repoType, index), repoType))
                .collect(Collectors.toList());
    }

    private String createRepoKey(RepoType repoType, int index) {
        return repoType.getDisplayName() + "-repo" + String.format("%03d", index);
    }

    private LocalRepoDescriptor createLocalRepoDescriptor(String repoKey, RepoType repoType) {
        LocalRepoDescriptor localRepoDescriptor = new LocalRepoDescriptor();
        localRepoDescriptor.setKey(repoKey);
        localRepoDescriptor.setType(repoType);
        return localRepoDescriptor;
    }

    private List<VirtualRepoDescriptor> createVirtualRepoDescriptors(RepoType repoType, int number) {
        return IntStream.range(1, number + 1)
                .mapToObj(index -> createVirtualRepoDescriptor(repoType.getDisplayName() +
                        "-virt-repo" + String.format("%03d", index), repoType))
                .collect(Collectors.toList());

    }

    private VirtualRepoDescriptor createVirtualRepoDescriptor(String repoKey, RepoType repoType) {
        VirtualRepoDescriptor virtualRepoDescriptor = new VirtualRepoDescriptor();
        virtualRepoDescriptor.setKey(repoKey);
        virtualRepoDescriptor.setType(repoType);
        return virtualRepoDescriptor;
    }

    private Map<Character, List<String>> createCharacterListMap(List<RepoDescriptor> repoDescriptors) {

        return StreamSupportUtils.stream(repoDescriptors)
                .map(RepoDescriptor::getKey)
                .filter(repo -> !StringUtils.equals(SystemRepos.USAGE_REPO_KEY, repo))
                .sorted(String::compareToIgnoreCase)
                .collect(Collectors.groupingBy(repoKey -> repoKey.toLowerCase().charAt(0)));
    }

    @DataProvider
    public Object[][] provideProjectKeys() {
        return new Object[][] {
                {DEFAULT_PROJECT},
                {PROJECT_A}
        };
    }
}
