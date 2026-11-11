package org.artifactory.queue;

import com.google.common.collect.Sets;
import jakarta.annotation.Nonnull;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.event.queue.QueueConfiguration;
import org.artifactory.event.queue.QueueConfigurationRegistrar;
import org.artifactory.event.queue.QueueCursorType;
import org.artifactory.event.queue.QueueEventImplementor;
import org.artifactory.event.queue.QueueOperations;
import org.artifactory.event.queue.QueueStatus;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.queue.register.PersistentQueueRegistrationService;
import org.artifactory.queue.register.PersistentQueueRegistrationServiceImpl;
import org.artifactory.queue.services.PersistentQueueCursorService;
import org.artifactory.queue.services.PersistentQueueCursorServiceImpl;
import org.artifactory.storage.db.event.model.NodeEventCursor;
import org.artifactory.storage.db.event.service.NodeEventCursorService;
import org.artifactory.storage.event.EventsService;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.artifactory.event.queue.QueueCursorType.FEDERATED_REPLICATION;
import static org.artifactory.event.queue.QueueCursorType.INCREMENTAL_INDEX;
import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Test
public class PersistentQueueRegistrationTest extends PersistentQueueBaseTest {

    private final ExampleLocalSingleQueueRegistry singleQueueRegistry = new ExampleLocalSingleQueueRegistry();

    public void noConfigurationsNoRepository() {
        PersistentQueueRegistrationService registrationService = RegistrationBuilder.builder().build();
        //check after init
        assertThat(registrationService.getAllQueues()).hasSize(0);
        assertThat(registrationService.getAllQueues()).hasSize(0);
        //check after register
        registrationService.registerRepository("my-local-maven");
        assertThat(registrationService.getAllQueues()).hasSize(0);
        //check after de-register
        registrationService.deRegisterRepository("my-local-maven");
        assertThat(registrationService.getAllQueues()).hasSize(0);
    }

    public void noConfigurationsOneRepository() {
        PersistentQueueRegistrationService registrationService = RegistrationBuilder.builder()
                .repoKeys("existing-repo")
                .build();
        //check after init
        assertThat(registrationService.getAllQueues()).hasSize(0);
        //check after register
        registrationService.registerRepository("my-local-maven");
        assertThat(registrationService.getAllQueues()).hasSize(0);
        //check after de-register
        registrationService.deRegisterRepository("my-local-maven");
        assertThat(registrationService.getAllQueues()).hasSize(0);
        registrationService.deRegisterRepository("existing-repo");
        assertThat(registrationService.getAllQueues()).hasSize(0);
    }

    public void oneConfigurationsOneRepository() {
        PersistentQueueRegistrationService registrationService = RegistrationBuilder.builder()
                .registrars(singleQueueRegistry)
                .repoKeys("existing-repo")
                .build();
        PersistentQueueRegistrationServiceImpl registrationServiceImpl =
                (PersistentQueueRegistrationServiceImpl) registrationService;
        //check after init
        assertThat(registrationService.getAllQueues()).hasSize(1);
        assertThat(registrationService.getQueues("existing-repo", INCREMENTAL_INDEX)).hasSize(1);
        verify(registrationServiceImpl).createQueue("existing-repo", false,
                singleQueueRegistry.getConfigurations("existing-repo").get(0));

        //check after register
        registrationService.registerRepository("my-local-maven");
        verify(registrationServiceImpl).createQueue("my-local-maven", true,
                singleQueueRegistry.getConfigurations("my-local-maven").get(0));
        assertThat(registrationService.getQueues("my-local-maven", INCREMENTAL_INDEX)).hasSize(1);
        assertThat(registrationService.getAllQueues()).hasSize(2);
        //check after de-register
        registrationService.deRegisterRepository("my-local-maven");
        assertThat(registrationService.getQueues("my-local-maven", INCREMENTAL_INDEX)).hasSize(0);
        assertThat(registrationService.getAllQueues()).hasSize(1);
        registrationService.deRegisterRepository("existing-repo");
        assertThat(registrationService.getAllQueues()).hasSize(0);
    }

    public void oneConfigurationMultipleRepositories() {
        PersistentQueueRegistrationService registrationService = RegistrationBuilder.builder()
                .registrars(singleQueueRegistry)
                .repoKeys("repo1", "repo2")
                .build();
        //check after init
        assertThat(registrationService.getAllQueues()).hasSize(2);
        assertThat(registrationService.getQueues("repo1", INCREMENTAL_INDEX)).hasSize(1);
        //check after register
        registrationService.registerRepository("my-local-maven");
        assertThat(registrationService.getAllQueues()).hasSize(3);
        //check after de-register
        registrationService.deRegisterRepository("my-local-maven");
        assertThat(registrationService.getAllQueues()).hasSize(2);
        registrationService.deRegisterRepository("repo1");
        registrationService.deRegisterRepository("repo2");
        assertThat(registrationService.getAllQueues()).hasSize(0);
    }

    public void noneMatchingRegistry() {
        PersistentQueueRegistrationService registrationService = RegistrationBuilder.builder()
                .registrars(new ExampleVirtualSingleQueueRegistry())
                .repoKeys("repo1", "repo2")
                .build();
        //check after init
        assertThat(registrationService.getAllQueues()).hasSize(0);
        //check after register
        registrationService.registerRepository("my-local-maven");
        assertThat(registrationService.getAllQueues()).hasSize(0);
        registrationService.deRegisterRepository("my-local-maven");
        registrationService.deRegisterRepository("repo1");
        registrationService.deRegisterRepository("repo2");
        assertThat(registrationService.getAllQueues()).hasSize(0);
    }

    public void multipleConfigurationMultipleRepositories() {
        PersistentQueueRegistrationService registrationService = RegistrationBuilder.builder()
                .registrars(singleQueueRegistry, new ExampleMultipleQueueRegistry("dest1", "dest2"))
                .repoKeys("repo1", "repo2")
                .build();
        //check after init
        assertThat(registrationService.getAllQueues()).hasSize(6);
        assertThat(registrationService.getQueues("repo1", INCREMENTAL_INDEX)).hasSize(1);
        assertThat(registrationService.getQueues("repo1", FEDERATED_REPLICATION)).hasSize(2);
        assertThat(registrationService.getQueueOperations("repo1", INCREMENTAL_INDEX)).isNotNull();
        assertThat(registrationService.getQueueOperations("repo1", FEDERATED_REPLICATION, "dest1")).isNotNull();
        assertThat(registrationService.getQueueOperations("repo1", FEDERATED_REPLICATION, "dest2")).isNotNull();
        //check after register
        registrationService.registerRepository("my-local-maven");
        assertThat(registrationService.getAllQueues()).hasSize(9);
        assertThat(registrationService.getQueues("my-local-maven", INCREMENTAL_INDEX)).hasSize(1);
        assertThat(registrationService.getQueues("my-local-maven", FEDERATED_REPLICATION)).hasSize(2);
        assertThat(registrationService.getQueues("repo1", INCREMENTAL_INDEX)).hasSize(1);
        assertThat(registrationService.getQueues("repo1", FEDERATED_REPLICATION)).hasSize(2);
        assertThat(registrationService.getQueueOperations("my-local-maven", INCREMENTAL_INDEX)).isNotNull();
        assertThat(registrationService.getQueueOperations("my-local-maven", FEDERATED_REPLICATION, "dest1"))
                .isNotNull();
        assertThat(registrationService.getQueueOperations("my-local-maven", FEDERATED_REPLICATION, "dest2"))
                .isNotNull();
        //check after de-register repository
        registrationService.deRegisterRepository("repo1");
        assertThat(registrationService.getAllQueues()).hasSize(6);
        assertThat(registrationService.getQueues("repo1", INCREMENTAL_INDEX)).hasSize(0);
        assertThat(registrationService.getQueues("repo1", FEDERATED_REPLICATION)).hasSize(0);
        //check after clean all
        registrationService.deRegisterRepository("my-local-maven");
        registrationService.deRegisterRepository("repo2");
        assertThat(registrationService.getAllQueues()).hasSize(0);
    }

    public void registerConfigurationsWithoutRegistrars() {
        PersistentQueueRegistrationService registrationService = RegistrationBuilder.builder()
                .repoKeys("repo1", "repo2")
                .build();
        assertThat(registrationService.getAllQueues()).hasSize(0);
        registrationService.register("repo1", createIncrementalIndexQueue());
        assertThat(registrationService.getAllQueues()).hasSize(1);
        assertThat(registrationService.getQueues("repo1", INCREMENTAL_INDEX)).hasSize(1);
        assertThat(registrationService.getQueueOperations("repo1", INCREMENTAL_INDEX)).isNotNull();
        registrationService.deRegisterRepository("repo1");
        assertThat(registrationService.getAllQueues()).hasSize(0);
        List<QueueConfiguration> configurations =
                new ExampleMultipleQueueRegistry("dest1", "dest2").getConfigurations("repo1");
        for (QueueConfiguration configuration : configurations) {
            registrationService.register("repo1", configuration);
        }
        assertThat(registrationService.getAllQueues()).hasSize(2);
        assertThat(registrationService.getQueues("repo1", FEDERATED_REPLICATION)).hasSize(2);
        assertThat(registrationService.getQueues("repo1", INCREMENTAL_INDEX)).hasSize(0);
        assertThat(registrationService.getQueueOperations("repo1", FEDERATED_REPLICATION, "dest1")).isNotNull();
        registrationService.register("repo1", createIncrementalIndexQueue());
        assertThat(registrationService.getAllQueues()).hasSize(3);
        assertThat(registrationService.getQueues("repo1", INCREMENTAL_INDEX)).hasSize(1);
        assertThat(registrationService.isRegistered("repo1", FEDERATED_REPLICATION, "dest1")).isTrue();
        assertThat(registrationService.isRegistered("repo1", INCREMENTAL_INDEX, "")).isTrue();
        //start de-register and check
        registrationService.deRegister("repo1", FEDERATED_REPLICATION, "dest1");
        assertThat(registrationService.isRegistered("repo1", FEDERATED_REPLICATION, "dest1")).isFalse();
        assertThat(registrationService.isRegistered("repo1", FEDERATED_REPLICATION, "dest2")).isTrue();
        registrationService.deRegister("repo1", INCREMENTAL_INDEX, "");
        assertThat(registrationService.isRegistered("repo1", INCREMENTAL_INDEX, "")).isFalse();
        registrationService.deRegisterRepository("repo1");
        assertThat(registrationService.isRegistered("repo1", FEDERATED_REPLICATION, "dest2")).isFalse();
        assertThat(registrationService.getAllQueues()).hasSize(0);
    }

    public void destroyRegistrationService() {
        PersistentQueueRegistrationService registrationService = RegistrationBuilder.builder()
                .repoKeys("repo1")
                .build();
        registrationService.register("repo1", createIncrementalIndexQueue());
        assertThat(registrationService.getAllQueues()).hasSize(1);
        registrationService.destroy();
        assertThat(registrationService.getAllQueues()).isEmpty();
    }

    @Test
    public void failedDuplicateConfigurations() {
        PersistentQueueRegistrationService registrationService = RegistrationBuilder.builder()
                .registrars(new ExampleLocalSingleQueueRegistry(), new ExampleLocalSingleQueueRegistry())
                .repoKeys("repo1")
                .build();
        Assert.assertEquals(registrationService.getAllQueues().size(), 1);
    }

    @Test
    public void failedRegisteringSameRepository() {
        PersistentQueueRegistrationService registrationService = RegistrationBuilder.builder()
                .registrars(singleQueueRegistry)
                .repoKeys("repo1")
                .build();
        registrationService.registerRepository("repo1");
        Assert.assertEquals(registrationService.getAllQueues().size(), 1);

    }

    @Test
    public void failedDuplicateConfigurationsWithVariants() {
        PersistentQueueRegistrationService registrationService = RegistrationBuilder.builder()
                .registrars(new ExampleMultipleQueueRegistry("dest1", "dest1"))
                .repoKeys("repo1")
                .build();
        Assert.assertEquals(registrationService.getAllQueues().size(), 1);
    }

    @Test(dataProvider = "provideSearchParams")
    public void checkRegistrationMethods(String repoKey, QueueCursorType cursorType, String var, boolean result) {
        PersistentQueueRegistrationService registrationService = RegistrationBuilder.builder()
                .registrars(singleQueueRegistry, new ExampleMultipleQueueRegistry("dest1", "dest2"))
                .repoKeys("repo1", "repo2")
                .build();
        assertThat(registrationService.isRegistered(repoKey, cursorType, var)).isEqualTo(result);
        assertThat(success(() -> registrationService.getQueueOperations(repoKey, cursorType, var)))
                .isEqualTo(result);
    }

    @Test(dataProvider = "provideCursorStatus")
    public void checkQueueStatus(long firstEvent, long currentEvent, QueueStatus expected) {
        //prepare mock cursor results
        NodeEventCursor nodeEventCursor = mock(NodeEventCursor.class);
        when(nodeEventCursor.getEventMarker()).thenReturn(currentEvent);
        when(eventsService.getFirstEventLogId(anyString())).thenReturn(firstEvent);
        when(nodeEventCursorService.cursorForOperator(anyString())).thenReturn(nodeEventCursor);
        //create cursorService
        PersistentQueueCursorService queueCursorService =
                new PersistentQueueCursorServiceImpl(nodeEventCursorService, eventsService, addonsManager);
        when(artifactoryContext.beanForType(PersistentQueueCursorService.class)).thenReturn(queueCursorService);
        //build registrationService
        PersistentQueueRegistrationService registrationService = RegistrationBuilder.builder()
                .registrars(singleQueueRegistry)
                .repoKeys("repo1")
                .build();

        QueueOperations queueOperations = registrationService.getQueueOperations("repo1", INCREMENTAL_INDEX);
        assertThat(queueOperations.getQueueStatus()).isEqualTo(expected);
    }

    @Test
    public void testRepoMinEventIdCache() throws Exception {
        try {
            ArtifactoryHome home = mock(ArtifactoryHome.class);
            ArtifactoryHome.bind(home);
            ArtifactorySystemProperties props = mock(ArtifactorySystemProperties.class);
            when(props.getLongProperty(eq(ConstantValues.persistentQueueRepoMinEventCacheExpireSec))).thenReturn(2L);
            when(props.getLongProperty(eq(ConstantValues.lastEventIdCacheTimeoutSec))).thenReturn(30L);
            when(home.getArtifactoryProperties()).thenReturn(props);

            PersistentQueueCursorService queueCursorService =
                    new PersistentQueueCursorServiceImpl(nodeEventCursorService, eventsService, addonsManager);

            when(nodeEventCursorService.cursorForOperator(eq("cursor1")))
                    .thenReturn(new NodeEventCursor("cursor1", 1L,
                            QueueCursorTypeMapper.get(INCREMENTAL_INDEX)));

            queueCursorService.getStatus("repo1", "cursor1");
            queueCursorService.getStatus("repo1", "cursor1");
            Thread.sleep(2000);
            queueCursorService.getStatus("repo1", "cursor1");
            verify(eventsService, times(2)).getFirstEventLogId(eq("repo1"));
        } finally {
            ArtifactoryHome.unbind();
        }
    }

    @Test(dataProvider = "provideOverflowValues")
    public void checkErrorOverflow(int errorCount, QueueStatus expected, int cursorInvocationCount) {
        when(queueErrorService.getErrorCount((any()))).thenReturn(errorCount);
        when(queueCursorService.getStatus(anyString(), anyString())).thenReturn(expected);
        when(artifactoryContext.beanForType(PersistentQueueCursorService.class)).thenReturn(queueCursorService);
        PersistentQueueRegistrationService registrationService = RegistrationBuilder.builder()
                .registrars(singleQueueRegistry)
                .repoKeys("repo1")
                .build();
        QueueOperations queueOperations = registrationService.getQueueOperations("repo1", INCREMENTAL_INDEX);
        assertThat(queueOperations.getQueueStatus()).isEqualTo(expected);
        verify(queueCursorService, times(cursorInvocationCount)).getStatus(anyString(), anyString());
    }

    @Test(description = "test copy queue creating new copy with the same variant")
    public void testRegistrationServiceCopyQueue() {
        String oldVariantBaseURL = "http://local:9092/artifactory";
        String newVariantBaseURL = "http://local:10102/artifactory";
        String repoKey = "repo-alpine";
        // set mock members expected
        String oldVariant = oldVariantBaseURL + '/' + repoKey;
        String newVariant = newVariantBaseURL + '/' + repoKey;

        // mock
        when(artifactoryContext.beanForType(PersistentQueueCursorService.class)).thenReturn(queueCursorService);
        NodeEventCursor nodeEventCursor = mock(NodeEventCursor.class);
        when(nodeEventCursorService.cursorForOperator(anyString())).thenReturn(nodeEventCursor);
        PersistentQueueCursorService queueCursorService =
                new PersistentQueueCursorServiceImpl(nodeEventCursorService, eventsService, addonsManager);
        when(artifactoryContext.beanForType(PersistentQueueCursorService.class)).thenReturn(queueCursorService);

        PersistentQueueRegistrationService registrationService = RegistrationBuilder.builder()
                .registrars(singleQueueRegistry, new ExampleMultipleQueueRegistry(oldVariant))
                .repoKeys(repoKey, "repo2")
                .build();
        // execute copy queue
        registrationService.copyQueue(repoKey, oldVariant, newVariant, FEDERATED_REPLICATION, mock(
                QueueEventImplementor.class));

        //test
        assertThat(registrationService.isRegistered(repoKey, FEDERATED_REPLICATION, newVariant)).isEqualTo(true);
        assertThat(success(() -> registrationService.getQueueOperations(repoKey, FEDERATED_REPLICATION, newVariant)))
                .isEqualTo(true);
    }


    private boolean success(Runnable callback) {
        try {
            callback.run();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    @DataProvider
    private Object[][] provideOverflowValues() {
        return new Object[][] {
                {0, QueueStatus.OK, 1},
                {999, QueueStatus.OK, 1},
                {1000, QueueStatus.ERROR_OVERFLOW, 0},
                {1001, QueueStatus.ERROR_OVERFLOW, 0},
        };
    }

    @DataProvider
    private Object[][] provideCursorStatus() {
        return new Object[][] {
                {0L, 0L, QueueStatus.OK},
                {1L, 1L, QueueStatus.OK},
                {1L, 2L, QueueStatus.OK},
                {1L, 10L, QueueStatus.OK},
                {10L, 0L, QueueStatus.DATA_LOST},
                {10L, 1L, QueueStatus.DATA_LOST},
                {10L, 8L, QueueStatus.DATA_LOST},
                {10L, 9L, QueueStatus.OK},
                {10L, 10L, QueueStatus.OK},
                {10L, 11L, QueueStatus.OK},
                {10L, 12L, QueueStatus.OK},
        };
    }

    @DataProvider
    private Object[][] provideSearchParams() {
        return new Object[][] {
                {"repo1", INCREMENTAL_INDEX, null, false},
                {"repo1", INCREMENTAL_INDEX, "", true},
                {"repo2", INCREMENTAL_INDEX, "", true},
                {"repo1", FEDERATED_REPLICATION, null, false},
                {"repo1", FEDERATED_REPLICATION, "", false},
                {"repo1", FEDERATED_REPLICATION, "dest1", true},
                {"repo2", FEDERATED_REPLICATION, "dest1", true},
        };
    }

    private static class RegistrationBuilder {
        private final List<String> repoKeys = new ArrayList<>();
        private final List<QueueConfigurationRegistrar> registrars = new ArrayList<>();

        static RegistrationBuilder builder() {
            return new RegistrationBuilder();
        }

        RegistrationBuilder repoKeys(String... repoKeys) {
            this.repoKeys.addAll(Arrays.asList(repoKeys));
            return this;
        }

        RegistrationBuilder registrars(QueueConfigurationRegistrar... registrars) {
            this.registrars.addAll(Arrays.asList(registrars));
            return this;
        }

        PersistentQueueRegistrationService build() {
            EventsService eventsService = mock(EventsService.class);
            PersistentQueueRegistrationService registrationService = spy(new PersistentQueueRegistrationServiceImpl(
                    registrars, List.of(), mockRepositoryService(repoKeys), mockPersistentQueueCursorService(),
                    eventsService, mock(NodeEventCursorService.class)));
            registrationService.init();
            return registrationService;
        }

        @Nonnull
        RepositoryService mockRepositoryService(List<String> repoKeys) {
            RepositoryService repositoryService = mock(RepositoryService.class);
            when(repositoryService.getAllRepoKeys()).thenReturn(Sets.newHashSet(repoKeys));
            LocalRepoConfig localRepoConfig = LocalRepoConfig.create("repo", RepoType.Maven);
            when(repositoryService.getRepoConfigByKey(any())).thenReturn(localRepoConfig);
            return repositoryService;
        }

        @Nonnull
        PersistentQueueCursorService mockPersistentQueueCursorService() {
            return mock(PersistentQueueCursorService.class);
        }
    }
}