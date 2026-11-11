package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes;

import org.artifactory.addon.AddonsManager;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.fs.FolderInfoImpl;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.storage.fs.service.PropertiesService;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.stream.Stream;

import static org.artifactory.api.repo.BaseBrowsableItem.UP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Tomer Slutsky
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class NodeComparatorTest extends ArtifactoryHomeBoundTest {
    @Mock
    private ArtifactoryContext context;
    private NodeComparator nodeComparator = new NodeComparator();
    private AutoCloseable mocks;

    private static Stream<Arguments> providerForNatural() {
        return Stream.of(
                Arguments.of(new FileNode(RepoPathFactory.create("a"), UP, "local", RepoType.Alpine, false),
                        new FileNode(RepoPathFactory.create("a"), UP, "local", RepoType.Alpine, false), -1),
                Arguments.of(new FileNode(RepoPathFactory.create("a"), "not", "local", RepoType.Alpine, false),
                        new FileNode(RepoPathFactory.create("a"), UP, "local", RepoType.Alpine, false), 1),
                Arguments.of(new FileNode(RepoPathFactory.create("a"), "not", "local", RepoType.Alpine, false),
                        new FileNode(RepoPathFactory.create("a"), "not", "local", RepoType.Alpine, false), 0),
                Arguments.of(new FolderNode(new FolderInfoImpl(RepoPathFactory.create("a")), "not", "local"),
                        new FileNode(RepoPathFactory.create("a"), UP, "local", RepoType.Alpine, false), -1),
                Arguments.of(new FileNode(RepoPathFactory.create("a"), "not", "local", RepoType.Alpine, false),
                        new FolderNode(new FolderInfoImpl(RepoPathFactory.create("a")), "not", "local"), 1),
                Arguments.of(new FolderNode(new FolderInfoImpl(RepoPathFactory.create("a")), UP, "local"),
                        new FolderNode(new FolderInfoImpl(RepoPathFactory.create("a")), UP, "local"), 0),
                Arguments.of(new FolderNode(new FolderInfoImpl(RepoPathFactory.create("a")), "not", "local"),
                        new FolderNode(new FolderInfoImpl(RepoPathFactory.create("a")), "not", "local"), 0)
        );
    }

    private static Stream<Arguments> providerForNumericFirst() {
        return Stream.of(
                Arguments.of(new FileNode(RepoPathFactory.create("a"), "multi10", "local", RepoType.Alpine, false),
                        new FileNode(RepoPathFactory.create("a"), "multi3", "local", RepoType.Alpine, false), 1),
                Arguments.of(new FileNode(RepoPathFactory.create("a"), "multi.10", "local", RepoType.Alpine, false),
                        new FileNode(RepoPathFactory.create("a"), "multi3", "local", RepoType.Alpine, false), 1),
                Arguments.of(new FileNode(RepoPathFactory.create("a"), "multi3", "local", RepoType.Alpine, false),
                        new FileNode(RepoPathFactory.create("a"), "multi10", "local", RepoType.Alpine, false), -1),
                Arguments.of(new FileNode(RepoPathFactory.create("a"), "10", "local", RepoType.Alpine, false),
                        new FileNode(RepoPathFactory.create("a"), "5.13.13", "local", RepoType.Alpine, false), 1),
                Arguments
                        .of(new FileNode(RepoPathFactory.create("a"), "10something10", "local", RepoType.Alpine, false),
                                new FileNode(RepoPathFactory.create("a"), "10something", "local", RepoType.Alpine,
                                        false), 1),
                Arguments.of(new FileNode(RepoPathFactory.create("a"), "10pre-SNAPSHOT", "local", RepoType.Alpine,
                                false),
                        new FileNode(RepoPathFactory.create("a"), "10pre-snapshot-1", "local", RepoType.Alpine, false),
                        -1)
        );
    }

    private static Stream<Arguments> providerForCompare() {
        return Stream.of(
                Arguments.of(new FileNode(RepoPathFactory.create("a"), "10", "local", RepoType.Alpine, false),
                        new FileNode(RepoPathFactory.create("a"), "3", "local", RepoType.Alpine, false), "false", -2),
                Arguments.of(new FileNode(RepoPathFactory.create("a"), "10.12.3", "local", RepoType.Alpine, false),
                        new FileNode(RepoPathFactory.create("a"), "3.4", "local", RepoType.Alpine, false), "true", 1),
                Arguments.of(new FileNode(RepoPathFactory.create("a"), "multi10", "local", RepoType.Alpine, false),
                        new FileNode(RepoPathFactory.create("a"), "multi2", "local", RepoType.Alpine, false), "true",
                        1),
                Arguments.of(new FolderNode(new FolderInfoImpl(RepoPathFactory.create("a")), "not", "local"),
                        new FileNode(RepoPathFactory.create("a"), "bla", "local", RepoType.Alpine, false), "true", -1),
                Arguments.of(new FileNode(RepoPathFactory.create("a"), "not", "local", RepoType.Alpine, false),
                        new FolderNode(new FolderInfoImpl(RepoPathFactory.create("a")), "not", "local"), "true", 1),
                Arguments.of(new FolderNode(new FolderInfoImpl(RepoPathFactory.create("a")), "multi10", "local"),
                        new FolderNode(new FolderInfoImpl(RepoPathFactory.create("a")), "multi3", "local"), "true", 1),
                Arguments.of(new FolderNode(new FolderInfoImpl(RepoPathFactory.create("a")), "not", "local"),
                        new FolderNode(new FolderInfoImpl(RepoPathFactory.create("a")), "not", "local"), "true", 0),
                Arguments.of(new FolderNode(new FolderInfoImpl(RepoPathFactory.create("a")), "2.3", "local"),
                        new FolderNode(new FolderInfoImpl(RepoPathFactory.create("a")), "12.3", "local"), "true", -1)
        );
    }

    @BeforeAll
    public void beforeAll() {
        mocks = MockitoAnnotations.openMocks(this);
        bindArtifactoryHome();
        ArtifactoryContextThreadBinder.bind(context);
        RepositoryService repositoryService = mock(RepositoryService.class);
        when(context.beanForType(RepositoryService.class)).thenReturn(repositoryService);
        LocalRepoDescriptor localRepoDescriptor = new LocalRepoDescriptor();
        localRepoDescriptor.setType(RepoType.Alpine);
        when(repositoryService.localFederatedCachedRepoDescriptorByKey(any())).thenReturn(localRepoDescriptor);
        when(context.beanForType(AddonsManager.class)).thenReturn(mock(AddonsManager.class));
        when(context.beanForType(PropertiesService.class)).thenReturn(mock(PropertiesService.class));
    }

    @AfterAll
    public void afterAll() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        unbindArtifactoryHome();
        mocks.close();
    }

    @BeforeEach
    public void setup() {
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.treebrowserNumericSort.getPropertyName(), "false");
    }

    @ParameterizedTest
    @MethodSource("providerForNumericFirst")
    public void testSortNumericFirst(BaseNode s1, BaseNode s2, int expectedValue) {
        assertEquals(nodeComparator.sortNumericFirst(s1, s2), expectedValue);
    }

    @ParameterizedTest
    @MethodSource("providerForNatural")
    public void testCompareNatural(BaseNode s1, BaseNode s2, int expectedValue) {
        assertEquals(nodeComparator.compareNatural(s1, s2), expectedValue);
    }

    @ParameterizedTest
    @MethodSource("providerForCompare")
    public void testCompare(BaseNode s1, BaseNode s2, String constValue, int expectedValue) {
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.treebrowserNumericSort.getPropertyName(), constValue);
        assertEquals(nodeComparator.compare(s1, s2), expectedValue);
    }
}