package org.artifactory.hqc.service.fullsync;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.apache.commons.io.FileUtils;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.status.StatusHolder;
import org.artifactory.federation.FullSyncException;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.system.SystemRepoConfigFactory;
import org.artifactory.security.SecurityServiceImpl;
import org.jfrog.storage.binstore.exceptions.BinaryRejectedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * @author Tomer Slutsky
 */
class FullSyncFileServiceImplTest {
    @Mock
    ArtifactoryContext context;
    @Mock
    private InternalRepositoryService repositoryService;
    @Mock
    private SecurityServiceImpl securityService;
    private File file;
    AutoCloseable autoCloseable;

    @BeforeEach
    void setUp() throws Exception {
        File tempDir = Files.createTempDirectory("temp").toFile();
        String tempWorkDir = tempDir.getAbsolutePath();
        file = new File(tempWorkDir + "/test");
        autoCloseable = MockitoAnnotations.openMocks(this);
        when(securityService.callAsSystem(any())).thenCallRealMethod();
        SystemRepoConfigFactory systemRepoConfigFactory = mock(SystemRepoConfigFactory.class);
        when(context.beanForType(SystemRepoConfigFactory.class)).thenReturn(systemRepoConfigFactory);
        doNothing().when(systemRepoConfigFactory).buildSystemRepo(any(), any());
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterEach
    void tearDown() throws Exception {
        autoCloseable.close();
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void testSaveFileToSystemRepo() {
        FullSyncFileServiceImpl recordService =
                spy(new FullSyncFileServiceImpl(securityService, repositoryService));
        StatusHolder statusHolder = mock(StatusHolder.class);
        when(repositoryService.deploy(any(), any())).thenReturn(statusHolder);
        try {
            FileUtils.touch(file);
            FullSyncRecord fullSyncRecord = recordService.saveFileToSystemRepo(file, 0, true);
            assertNotNull(fullSyncRecord);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            file.delete();
        }
    }

    @Test
    void testSaveFileToSystemRepoNoTimestamp() {
        FullSyncFileServiceImpl recordService =
                spy(new FullSyncFileServiceImpl(securityService, repositoryService));
        StatusHolder statusHolder = mock(StatusHolder.class);
        when(repositoryService.deploy(any(), any())).thenReturn(statusHolder);
        try {
            FileUtils.touch(file);
            FullSyncRecord fullSyncRecord = recordService.saveFileToSystemRepo(file);
            assertNotNull(fullSyncRecord);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            file.delete();
        }
    }

    @Test
    void testSaveFileToSystemRepoFails() {
        FullSyncFileServiceImpl recordService =
                spy(new FullSyncFileServiceImpl(securityService, repositoryService));
        StatusHolder statusHolder = mock(StatusHolder.class);
        when(statusHolder.isError()).thenReturn(true);
        when(repositoryService.deploy(any(), any())).thenReturn(statusHolder);
        try {
            FileUtils.touch(file);
            assertThrows(FullSyncException.class, () -> recordService.saveFileToSystemRepo(file, 0, true));
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            file.delete();
        }
    }

    @Test
    void testSaveFileToNullSystemRepo() {
        FullSyncFileServiceImpl recordService =
                spy(new FullSyncFileServiceImpl(securityService, repositoryService));
        when(repositoryService.existsByRepoKey("jfrog-full-sync-info"))
                .thenReturn(false);
        SystemRepoConfigFactory systemRepoConfigFactory = mock(SystemRepoConfigFactory.class);
        when(context.beanForType(SystemRepoConfigFactory.class)).thenReturn(systemRepoConfigFactory);
        doNothing().when(systemRepoConfigFactory).buildSystemRepo(any(), any());
        StatusHolder statusHolder = mock(StatusHolder.class);
        when(repositoryService.deploy(any(), any())).thenReturn(statusHolder);
        try {
            FileUtils.touch(file);
            FullSyncRecord fullSyncRecord = recordService.saveFileToSystemRepo(file, 0, true);
            assertNotNull(fullSyncRecord);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            file.delete();
        }
    }

    @Test
    void testSaveFileToThrowsException() {
        FullSyncFileServiceImpl recordService =
                spy(new FullSyncFileServiceImpl(securityService, repositoryService));
        when(repositoryService.existsByRepoKey("jfrog-full-sync-info")).thenReturn(false);
        SystemRepoConfigFactory systemRepoConfigFactory = mock(SystemRepoConfigFactory.class);
        when(context.beanForType(SystemRepoConfigFactory.class)).thenReturn(systemRepoConfigFactory);
        doNothing().when(systemRepoConfigFactory).buildSystemRepo(any(), any());
        when(repositoryService.deploy(any(), any())).thenThrow(BinaryRejectedException.class);
        try {
            FileUtils.touch(file);
            FullSyncException exception =
                    assertThrows(FullSyncException.class, () -> recordService.saveFileToSystemRepo(file, 0, true));
            assertTrue(exception.getMessage().contains("Failed to save file"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            file.delete();
        }
    }

    @Test
    void testUndeployFromRepo() {
        Logger logger = (Logger) LoggerFactory.getLogger(FullSyncFileServiceImpl.class);
        // given
        logger.setLevel(Level.TRACE);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
        FullSyncFileServiceImpl recordService =
                spy(new FullSyncFileServiceImpl(securityService, repositoryService));
        when(repositoryService.undeploy(any())).thenReturn(mock(BasicStatusHolder.class));
        recordService.undeployFromRepo("file");
        List<ILoggingEvent> iLoggingEvents =
                listAppender.list.stream().filter(event -> event.getLevel().equals(Level.TRACE)).toList();
        assertEquals(2, iLoggingEvents.size());
        assertTrue(
                iLoggingEvents.get(0).getMessage().contains("Trying to undeploy from full sync repo due to an error"));
        assertTrue(iLoggingEvents.get(1).getMessage().contains("Undeploy from full sync repo was successful"));
    }

    @Test
    void testUndeployFromRepoFails() {
        Logger logger = (Logger) LoggerFactory.getLogger(FullSyncFileServiceImpl.class);
        // given
        logger.setLevel(Level.TRACE);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
        FullSyncFileServiceImpl recordService =
                spy(new FullSyncFileServiceImpl(securityService, repositoryService));
        BasicStatusHolder statusHolder = mock(BasicStatusHolder.class);
        when(statusHolder.hasErrors()).thenReturn(true);
        when(repositoryService.undeploy(any())).thenReturn(statusHolder);
        recordService.undeployFromRepo("file");
        List<ILoggingEvent> iLoggingEvents =
                listAppender.list.stream().filter(event -> event.getLevel().equals(Level.TRACE)).toList();
        assertEquals(2, iLoggingEvents.size());
        assertTrue(
                iLoggingEvents.get(0).getMessage().contains("Trying to undeploy from full sync repo due to an error"));
        assertTrue(iLoggingEvents.get(1).getMessage().contains("Failed to undeploy due to"));
    }
}