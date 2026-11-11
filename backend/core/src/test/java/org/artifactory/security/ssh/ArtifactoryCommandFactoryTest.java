package org.artifactory.security.ssh;

import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.shell.UnknownCommand;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.security.ssh.command.CliAuthenticateCommand;
import org.artifactory.security.ssh.command.GitLfsAuthenticateCommand;
import org.artifactory.storage.security.service.UserGroupStoreService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ArtifactoryCommandFactoryTest {

    private ArtifactoryCommandFactory artifactoryCommandFactory;
    @Mock
    private CentralConfigService centralConfigService;
    @Mock
    private UserGroupStoreService userGroupStoreService;
    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        artifactoryCommandFactory = Mockito.spy(new ArtifactoryCommandFactory(centralConfigService,
                userGroupStoreService, mock(ArtifactoryHome.class), mock(ArtifactoryContext.class)));
    }

    @AfterEach
    void tearDown() throws Exception {
        closeable.close();
    }

    @Test
    void testCreateGitLfsCommand() {
        Command command = artifactoryCommandFactory.createCommand(null, "git-lfs-authenticate " +
                "/artifactory/context param0 param1");
        assertNotNull(command);
        assertTrue(command instanceof GitLfsAuthenticateCommand);
    }

    @Test
    void testCreateNonGitLfsCommand() {
        Command command = artifactoryCommandFactory.createCommand(null, "other /artifactory/context " +
                "param0 param1");
        assertNotNull(command);
        assertTrue(command instanceof UnknownCommand);
    }

    @Test
    void testCreateWithEmptyCommand() {
        Command command = artifactoryCommandFactory.createCommand(null, "");
        assertNotNull(command);
        assertTrue(command instanceof CliAuthenticateCommand);
    }
}