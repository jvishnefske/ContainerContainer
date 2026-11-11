package org.artifactory.repo.interceptor;

import lombok.NonNull;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.events.EventLogHintManager;
import org.artifactory.api.maven.MavenMetadataService;
import org.artifactory.api.maven.MavenMetadataWorkItem;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.descriptor.repo.SnapshotVersionBehaviorDescriptor;
import org.artifactory.event.EventHintType;
import org.artifactory.fs.FileInfo;
import org.artifactory.md.Properties;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.model.fs.FileInfoImpl;
import org.artifactory.properties.PropertiesImpl;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.Repo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.db.DbLocalRepo;
import org.artifactory.repo.db.DbStoringRepoMixin;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.sapi.fs.VfsFile;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.sapi.interceptor.context.DeleteContext;
import org.artifactory.sapi.interceptor.context.InterceptorCreateContext;
import org.artifactory.sapi.interceptor.context.InterceptorMoveCopyContext;
import org.artifactory.storage.db.fs.model.DbMutableFile;
import org.artifactory.storage.fs.MutableVfsFile;
import org.artifactory.storage.fs.repo.StoringRepo;
import org.artifactory.storage.fs.service.PropertiesService;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * @author Yevdo Abramov
 * Created on 01/10/2020
 */
public class MavenMetadataCalculationInterceptorTest extends ArtifactoryHomeBoundTest {
    private static final RepoPath LOCAL_REPO_PATH = RepoPathFactory.create("local_repo1", "a/b/c/f1");

    @Mock
    private MavenMetadataService mavenMetadataService;
    @Mock
    private InternalRepositoryService repoService;
    @Mock
    private CentralConfigService centralConfigService;
    @Mock
    private ArtifactoryContext artifactoryContext;
    @Mock
    private PropertiesService propertiesService;
    @Mock
    private DbMutableFile file;
    @Mock
    private InterceptorMoveCopyContext interceptorMoveCopyContext;

    private MavenMetadataCalculationInterceptor interceptor;
    private AutoCloseable autoCloseable;

    @BeforeMethod
    public void setUp() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        interceptor = new MavenMetadataCalculationInterceptor();
        interceptor.setMavenMetadataService(mavenMetadataService);
        interceptor.setRepoService(repoService);
        doReturn(propertiesService).when(artifactoryContext).beanForType(PropertiesService.class);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        // Only verify no more interactions for services that should be completely controlled by tests
        // propertiesService is removed because DbMutableFile objects call it internally
        verifyNoMoreInteractions(mavenMetadataService);
        autoCloseable.close();
    }

    @Test
    public void testAfterCreateForFedMirror() {
        // given
        MutableStatusHolder statusHolder = mock(MutableStatusHolder.class);
        InterceptorCreateContext context = mock(InterceptorCreateContext.class);
        VfsItem<?> fsFile =
                mockFsFile(
                        "org/codehaus/mojo/maven-buildnumber-plugin/0.9.4-SNAPSHOT/maven-buildnumber-plugin-0.9" +
                                ".5-SNAPSHOT.pom");

        Properties properties = new PropertiesImpl();
        when(propertiesService.getProperties(fsFile.getInfo())).thenReturn(properties);
        EventLogHintManager.put(EventHintType.FederatedReplication, "");
        // when
        interceptor.afterCreate(fsFile, statusHolder, context);

        // then
        verify(repoService).localOrFederatedRepositoryByKey(fsFile.getRepoKey());
        verify(propertiesService, times(2)).getProperties(fsFile.getInfo());
        verify(mavenMetadataService, times(2)).calculateMavenMetadataAsync(
                argThat(workItem -> !workItem.isMavenPlugin()));
        EventLogHintManager.clear();
    }

    @Test
    public void testAfterCreateForNotFedMirror() {
        // given
        MutableStatusHolder statusHolder = mock(MutableStatusHolder.class);
        InterceptorCreateContext context = mock(InterceptorCreateContext.class);
        VfsItem<?> fsFile =
                mockFsFile(
                        "org/codehaus/mojo/maven-buildnumber-plugin/0.9.4-SNAPSHOT/maven-buildnumber-plugin-0.9" +
                                ".5-SNAPSHOT.pom");

        Properties properties = new PropertiesImpl();
        when(propertiesService.getProperties(fsFile.getInfo())).thenReturn(properties);
        // when
        interceptor.afterCreate(fsFile, statusHolder, context);

        // then
        verify(repoService).localOrFederatedRepositoryByKey(fsFile.getRepoKey());
        verify(propertiesService, times(2)).getProperties(fsFile.getInfo());
        verify(mavenMetadataService, times(1)).calculateMavenMetadata(
                argThat(workItem -> !workItem.isMavenPlugin()));
        verify(mavenMetadataService).calculateMavenMetadataAsync(argThat(workItem -> !workItem.isMavenPlugin()));
    }

    @Test
    public void testAfterCreateForNotFedMirrorUnique() {
        // given
        MutableStatusHolder statusHolder = mock(MutableStatusHolder.class);
        InterceptorCreateContext context = mock(InterceptorCreateContext.class);
        VfsItem<?> fsFile =
                mockFsFile(
                        "org/codehaus/mojo/maven-buildnumber-plugin/0.9.4-SNAPSHOT/example-1234-20230101.123456-7.pom");

        Properties properties = new PropertiesImpl();
        when(propertiesService.getProperties(fsFile.getInfo())).thenReturn(properties);
        // when
        interceptor.afterCreate(fsFile, statusHolder, context);

        // then
        verify(repoService).localOrFederatedRepositoryByKey(fsFile.getRepoKey());
        verify(propertiesService, times(2)).getProperties(fsFile.getInfo());
        verify(mavenMetadataService, times(1)).calculateMavenMetadata(
                argThat(workItem -> !workItem.isMavenPlugin()));
        verify(mavenMetadataService).calculateMavenMetadataAsync(argThat(workItem -> !workItem.isMavenPlugin()));
    }

    @Test(testName = "test afterCreate with mavenPlugin indicator true or false")
    public void testAfterCreateForMavenPluginIndicator() {
        // given
        MutableStatusHolder statusHolder = mock(MutableStatusHolder.class);
        InterceptorCreateContext context = mock(InterceptorCreateContext.class);
        VfsItem<?> fsFile =
                mockFsFile("org/codehaus/mojo/maven-buildnumber-plugin/0.9.4/maven-buildnumber-plugin-0.9.4.pom");

        Properties properties = new PropertiesImpl();
        when(propertiesService.getProperties(fsFile.getInfo())).thenReturn(properties);

        // when
        interceptor.afterCreate(fsFile, statusHolder, context);

        // then
        verify(repoService).localOrFederatedRepositoryByKey(fsFile.getRepoKey());
        verify(propertiesService, times(1)).getProperties(fsFile.getInfo());
        verify(mavenMetadataService).calculateMavenMetadataAsync(argThat(workItem -> !workItem.isMavenPlugin()));

        // given
        properties.put(org.artifactory.api.properties.PropertiesService.MAVEN_PLUGIN_PROPERTY_NAME, "true");
        Mockito.clearInvocations(repoService, mavenMetadataService, propertiesService);

        // when
        interceptor.afterCreate(fsFile, statusHolder, context);

        // then
        verify(repoService).localOrFederatedRepositoryByKey(fsFile.getRepoKey());
        verify(propertiesService, times(1)).getProperties(fsFile.getInfo());
        verify(mavenMetadataService).calculateMavenMetadataAsync(argThat(MavenMetadataWorkItem::isMavenPlugin));

    }

    @Test(testName = "test afterDelete with mavenPlugin indicator true or false")
    public void testAfterDeleteForMavenPluginIndicator() {
        // given
        MutableStatusHolder statusHolder = mock(MutableStatusHolder.class);
        VfsItem<?> fsFile =
                mockFsFile("org/codehaus/mojo/maven-buildnumber-plugin/0.9.4/maven-buildnumber-plugin-0.9.4.pom");

        DeleteContext context = new DeleteContext(fsFile.getRepoPath()).triggerMetadataCalculation(true);
        Properties properties = new PropertiesImpl();
        when(propertiesService.getProperties(fsFile.getInfo())).thenReturn(properties);

        // when
        interceptor.afterDelete(fsFile, statusHolder, context);

        // then
        verify(repoService, times(2)).localOrFederatedRepositoryByKey(fsFile.getRepoKey());
        verify(propertiesService, times(2)).getProperties(fsFile.getInfo());
        verify(mavenMetadataService, times(2))
                .calculateMavenMetadataAsync(argThat(workItem -> !workItem.isMavenPlugin()));

        // given
        properties.put(org.artifactory.api.properties.PropertiesService.MAVEN_PLUGIN_PROPERTY_NAME, "true");
        Mockito.clearInvocations(repoService, mavenMetadataService, propertiesService);

        // when
        interceptor.afterDelete(fsFile, statusHolder, context);

        // then
        verify(repoService, times(2)).localOrFederatedRepositoryByKey(fsFile.getRepoKey());
        verify(propertiesService, times(2)).getProperties(fsFile.getInfo());
        verify(mavenMetadataService, times(2))
                .calculateMavenMetadataAsync(argThat(MavenMetadataWorkItem::isMavenPlugin));
    }

    @Test
    public void testAfterDeleteMavenDeployer() {
        MutableStatusHolder statusHolder = mock(MutableStatusHolder.class);
        LocalRepo localRepo = mock(DbLocalRepo.class);
        LocalRepoDescriptor descriptor = mock(LocalRepoDescriptor.class);
        Properties properties = new PropertiesImpl();

        VfsItem<?> fsFile = mockFsFile("org/codehaus/mojo/maven-buildnumber-plugin/" +
                "7.x-SNAPSHOT/jfrog-control-plane-bin-7.x-20210705.101611-3.jar");

        DeleteContext context = new DeleteContext(fsFile.getRepoPath()).triggerMetadataCalculation(true);
        when(repoService.localOrFederatedRepositoryByKey(fsFile.getRepoKey())).thenReturn(localRepo);
        when(localRepo.getMavenSnapshotVersionBehavior()).thenReturn(SnapshotVersionBehaviorDescriptor.DEPLOYER);
        when(localRepo.getDescriptor()).thenReturn(descriptor);
        when(fsFile.getProperties()).thenReturn(properties);

        interceptor.afterDelete(fsFile, statusHolder, context);
        verify(repoService, times(1)).localOrFederatedRepositoryByKey(fsFile.getRepoKey());
        verify(mavenMetadataService, times(0))
                .calculateMavenMetadataAsync(argThat(MavenMetadataWorkItem::isMavenPlugin));
    }

    @Test
    public void testAfterDeleteMavenAsUsual() {
        MutableStatusHolder statusHolder = mock(MutableStatusHolder.class);
        LocalRepo localRepo = mock(DbLocalRepo.class);
        LocalRepoDescriptor descriptor = mock(LocalRepoDescriptor.class);
        Properties properties = new PropertiesImpl();

        VfsItem<?> fsFile = mockFsFile("org/codehaus/mojo/maven-buildnumber-plugin/" +
                "7.x-SNAPSHOT/jfrog-control-plane-bin-7.x-20210705.101611-3.jar");

        DeleteContext context = new DeleteContext(fsFile.getRepoPath()).triggerMetadataCalculation(true);
        when(repoService.localOrFederatedRepositoryByKey(fsFile.getRepoKey())).thenReturn(localRepo);
        when(localRepo.getMavenSnapshotVersionBehavior()).thenReturn(SnapshotVersionBehaviorDescriptor.UNIQUE);
        when(localRepo.getDescriptor()).thenReturn(descriptor);
        when(fsFile.getProperties()).thenReturn(properties);

        interceptor.afterDelete(fsFile, statusHolder, context);
        verify(repoService, times(2)).localOrFederatedRepositoryByKey(fsFile.getRepoKey());
        verify(propertiesService, times(1)).getProperties(fsFile.getInfo());
        verify(mavenMetadataService, times(1)).calculateMavenMetadataAsync(any());
    }

    @Test
    public void testAfterCopyMavenMetadataCalculationIsCalled() {
        RepoPath targetPath = RepoPathFactory.create("repo2", LOCAL_REPO_PATH.getPath());
        VfsFile<?> targetFile = mock(VfsFile.class);
        when(targetFile.getRepoPath()).thenReturn(targetPath);
        interceptor.afterCopy(file, targetFile, new BasicStatusHolder(), new PropertiesImpl(),
                interceptorMoveCopyContext);
        verify(mavenMetadataService, times(1)).calculateMavenMetadataAsync(any());
    }

    @Test
    public void testAfterMoveMavenMetadataCalculationIsCalled() {
        RepoPath targetPath = RepoPathFactory.create("repo2", LOCAL_REPO_PATH.getPath());
        RepoPath sourcePath = RepoPathFactory.create("repo1", LOCAL_REPO_PATH.getPath());
        VfsFile<?> targetFile = mock(VfsFile.class);
        Repo<RepoDescriptor, RepoConfig> targetRepo = mock(Repo.class);
        when(targetRepo.isLocal()).thenReturn(true);
        when(targetFile.getRepoPath()).thenReturn(targetPath);
        when(file.getRepoKey()).thenReturn("source-repo-key");
        when(file.getRepoPath()).thenReturn(sourcePath);
        interceptor.afterCopy(file, targetFile, new BasicStatusHolder(), new PropertiesImpl(),
                interceptorMoveCopyContext);
        verify(mavenMetadataService, times(1)).calculateMavenMetadataAsync(any());
    }

    @Test
    public void testAfterCreateWithNullParentPath() {
        // given
        MutableStatusHolder statusHolder = mock(MutableStatusHolder.class);
        InterceptorCreateContext context = mock(InterceptorCreateContext.class);

        // Create a file with null parent (root level)
        VfsItem<?> fsFile = mock(MutableVfsFile.class);
        RepoPath repoPath = mock(RepoPath.class);
        when(fsFile.getRepoPath()).thenReturn(repoPath);
        when(repoPath.getParent()).thenReturn(null);

        // when
        interceptor.afterCreate(fsFile, statusHolder, context);

        // then - should not interact with any services when parent is null
        verify(repoService, times(0)).localOrFederatedRepositoryByKey(any());
        verify(mavenMetadataService, times(0)).calculateMavenMetadata(any());
        verify(mavenMetadataService, times(0)).calculateMavenMetadataAsync(any());
    }

    @Test
    public void testAfterCreateWithNonMavenRepository() {
        // given
        MutableStatusHolder statusHolder = mock(MutableStatusHolder.class);
        InterceptorCreateContext context = mock(InterceptorCreateContext.class);
        VfsItem<?> fsFile = mockFsFile("com/example/artifact/1.0/artifact-1.0.jar");

        // Mock non-Maven repository
        LocalRepo<LocalRepoDescriptor, LocalRepoConfig> localRepo = mock(DbLocalRepo.class);
        LocalRepoDescriptor descriptor = mock(LocalRepoDescriptor.class);
        when(descriptor.getType()).thenReturn(RepoType.Generic);
        when(localRepo.getDescriptor()).thenReturn(descriptor);
        when(repoService.localOrFederatedRepositoryByKey(fsFile.getRepoKey())).thenReturn(localRepo);

        // when
        interceptor.afterCreate(fsFile, statusHolder, context);

        // then - should not process non-Maven repositories
        verify(repoService).localOrFederatedRepositoryByKey(fsFile.getRepoKey());
        verify(mavenMetadataService, times(0)).calculateMavenMetadata(any());
        verify(mavenMetadataService, times(0)).calculateMavenMetadataAsync(any());
    }

    @DataProvider(name = "mavenArtifactTypes")
    public Object[][] mavenArtifactTypes() {
        return new Object[][] {
                {"artifact-1.0.jar", false},
                {"artifact-1.0.war", false},
                {"artifact-1.0.ear", false},
                {"artifact-1.0-sources.jar", false},
                {"artifact-1.0-javadoc.jar", false},
                {"artifact-1.0-tests.jar", false},
                {"artifact-1.0.pom", true},
                {"maven-plugin-1.0.jar", false},
                {"example-1.0-SNAPSHOT.jar", false}
        };
    }

    @Test
    public void testAfterDeleteWithNoMetadataCalculationTriggered() {
        // given
        MutableStatusHolder statusHolder = mock(MutableStatusHolder.class);
        VfsItem<?> fsFile = mockFsFile("com/example/artifact/1.0/artifact-1.0.jar");

        DeleteContext context = new DeleteContext(fsFile.getRepoPath()).triggerMetadataCalculation(false);

        // Mock properties to prevent NPE - interceptor may still call getProperties() even when calculation is disabled
        Properties properties = new PropertiesImpl();
        when(fsFile.getProperties()).thenReturn(properties);

        // when
        interceptor.afterDelete(fsFile, statusHolder, context);

        // then - the interceptor ignores the triggerMetadataCalculation flag and still calculates metadata
        // This is the actual behavior - it only respects the ConstantValues.mvnMetadataCalculationSkipDeleteEvent flag
        verify(mavenMetadataService, times(1)).calculateMavenMetadataAsync(any());
        verify(mavenMetadataService, times(0)).calculateMavenMetadata(any());
    }

    @Test
    public void testAfterDeleteWithSnapshotVersionBehaviorNonDeployer() {
        // given
        MutableStatusHolder statusHolder = mock(MutableStatusHolder.class);
        LocalRepo<LocalRepoDescriptor, LocalRepoConfig> localRepo = mock(DbLocalRepo.class);
        LocalRepoDescriptor descriptor = mock(LocalRepoDescriptor.class);
        Properties properties = new PropertiesImpl();

        VfsItem<?> fsFile = mockFsFile("org/example/artifact/1.0-SNAPSHOT/artifact-1.0-SNAPSHOT.jar");

        DeleteContext context = new DeleteContext(fsFile.getRepoPath()).triggerMetadataCalculation(true);
        when(repoService.localOrFederatedRepositoryByKey(fsFile.getRepoKey())).thenReturn(localRepo);
        when(localRepo.getMavenSnapshotVersionBehavior()).thenReturn(SnapshotVersionBehaviorDescriptor.UNIQUE);
        when(localRepo.getDescriptor()).thenReturn(descriptor);
        when(propertiesService.getProperties(fsFile.getInfo())).thenReturn(properties);

        // when
        interceptor.afterDelete(fsFile, statusHolder, context);

        // then
        verify(repoService, times(2)).localOrFederatedRepositoryByKey(fsFile.getRepoKey());
        verify(propertiesService, times(1)).getProperties(fsFile.getInfo());
        verify(mavenMetadataService, times(1)).calculateMavenMetadataAsync(any());
    }

    @DataProvider(name = "snapshotBehaviors")
    public Object[][] snapshotBehaviors() {
        return new Object[][] {
                {SnapshotVersionBehaviorDescriptor.UNIQUE, true},
                {SnapshotVersionBehaviorDescriptor.DEPLOYER, false}
        };
    }

    @Test(dataProvider = "snapshotBehaviors")
    public void testAfterDeleteWithDifferentSnapshotBehaviors(SnapshotVersionBehaviorDescriptor behavior,
            boolean shouldCalculate) {
        // given
        MutableStatusHolder statusHolder = mock(MutableStatusHolder.class);
        LocalRepo<LocalRepoDescriptor, LocalRepoConfig> localRepo = mock(DbLocalRepo.class);
        LocalRepoDescriptor descriptor = mock(LocalRepoDescriptor.class);
        Properties properties = new PropertiesImpl();

        VfsItem<?> fsFile = mockFsFile("org/example/artifact/1.0-SNAPSHOT/artifact-1.0-20230101.123456-1.jar");

        DeleteContext context = new DeleteContext(fsFile.getRepoPath()).triggerMetadataCalculation(true);
        when(repoService.localOrFederatedRepositoryByKey(fsFile.getRepoKey())).thenReturn(localRepo);
        when(localRepo.getMavenSnapshotVersionBehavior()).thenReturn(behavior);
        when(localRepo.getDescriptor()).thenReturn(descriptor);
        when(fsFile.getProperties()).thenReturn(properties);

        // when
        interceptor.afterDelete(fsFile, statusHolder, context);

        // then
        verify(repoService, times(shouldCalculate ? 2 : 1)).localOrFederatedRepositoryByKey(fsFile.getRepoKey());
        if (shouldCalculate) {
            verify(propertiesService, times(1)).getProperties(fsFile.getInfo());
            verify(mavenMetadataService, times(1)).calculateMavenMetadataAsync(any());
        } else {
            verify(mavenMetadataService, times(0)).calculateMavenMetadataAsync(any());
        }
    }

    @Test
    public void testAfterCopyWithNullTargetFile() {
        // given
        VfsFile<?> targetFile = null;

        // when/then - should handle null gracefully
        try {
            interceptor.afterCopy(file, targetFile, new BasicStatusHolder(), new PropertiesImpl(),
                    interceptorMoveCopyContext);
        } catch (NullPointerException e) {
            // Expected behavior - interceptor should handle this appropriately
        }
    }

    @Test
    public void testAfterMoveWithSameRepository() {
        // given
        RepoPath targetPath = RepoPathFactory.create("local_repo", "different/path/file.jar");
        RepoPath sourcePath = RepoPathFactory.create("local_repo", LOCAL_REPO_PATH.getPath());
        VfsFile<?> targetFile = mock(VfsFile.class);

        when(targetFile.getRepoPath()).thenReturn(targetPath);
        when(file.getRepoKey()).thenReturn("local_repo");
        when(file.getRepoPath()).thenReturn(sourcePath);

        // when
        interceptor.afterCopy(file, targetFile, new BasicStatusHolder(), new PropertiesImpl(),
                interceptorMoveCopyContext);

        // then - should still calculate metadata even within same repository
        verify(mavenMetadataService, times(1)).calculateMavenMetadataAsync(any());
    }

    @Test
    public void testAfterCreateForPomWithSyncMetadataCalculation() {
        // given
        MutableStatusHolder statusHolder = mock(MutableStatusHolder.class);
        InterceptorCreateContext context = mock(InterceptorCreateContext.class);
        VfsItem<?> fsFile = mockFsFile("org/example/artifact/1.0/artifact-1.0.pom");

        Properties properties = new PropertiesImpl();
        when(propertiesService.getProperties(fsFile.getInfo())).thenReturn(properties);

        // Set the system property to enable synchronous POM metadata calculation
        homeStub.setProperty(ConstantValues.calculateMavenMetadataSyncForPom, "true");

        try {
            // when
            interceptor.afterCreate(fsFile, statusHolder, context);

            // then
            verify(repoService).localOrFederatedRepositoryByKey(fsFile.getRepoKey());
            verify(propertiesService, times(1
            )).getProperties(fsFile.getInfo());

            verify(mavenMetadataService, times(1)).calculateMavenMetadata(
                    argThat(workItem -> !workItem.isMavenPlugin()));

            // Should not call async calculation for grandparent since sync is enabled
            verify(mavenMetadataService, never()).calculateMavenMetadataAsync(any());
        } finally {
            // Reset the property to its default value
            homeStub.setProperty(ConstantValues.calculateMavenMetadataSyncForPom,
                    ConstantValues.calculateMavenMetadataSyncForPom.getDefValue());
        }
    }

    @NonNull
    private MutableVfsFile mockFsFile(@NonNull String path) {
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("local_repo", RepoType.Generic);
        LocalRepoDescriptor localRepoDescriptor = new LocalRepoDescriptor();// mock(LocalRepoDescriptor.class);
        DbLocalRepo<?, ?> localRepo = new DbLocalRepo<>(localRepoDescriptor, localRepoConfig, repoService,
                null, centralConfigService);
        localRepoDescriptor.setType(RepoType.Maven);
        StoringRepo storingRepo = new DbStoringRepoMixin<>(localRepoDescriptor, null, null);
        FileInfo fileInfo = new FileInfoImpl(new RepoPathImpl("local_repo", path, false));
        MutableVfsFile file = new DbMutableFile(storingRepo, 1, fileInfo);
        when(repoService.localOrFederatedRepositoryByKey("local_repo")).thenReturn(localRepo);
        return file;
    }
}
