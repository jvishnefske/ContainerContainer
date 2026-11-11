/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.security.ssh;

import org.apache.sshd.common.keyprovider.FileKeyPairProvider;
import org.apache.sshd.server.ServerBuilder;
import org.apache.sshd.server.SshServer;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.config.CentralConfigKey;
import org.artifactory.config.InternalCentralConfigService;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.security.SecurityDescriptor;
import org.artifactory.descriptor.security.sshserver.SshServerSettings;
import org.artifactory.security.ssh.algorithms.CipherAlgorithms;
import org.artifactory.security.ssh.algorithms.KeyExchangeAlgorithms;
import org.artifactory.security.ssh.algorithms.MacAlgorithms;
import org.artifactory.security.ssh.algorithms.SignatureAlgorithms;
import org.artifactory.spring.Reloadable;
import org.artifactory.storage.security.service.UserGroupStoreService;
import org.artifactory.version.CompoundVersionDetails;
import org.jfrog.common.config.diff.DataDiff;
import org.jfrog.config.watch.FileEventType;
import org.jfrog.security.file.SecurityFolderHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.jfrog.security.file.SecurityFolderHelper.PERMISSIONS_MODE_600;

/**
 * @author Noam Y. Tenne
 * @author Chen Keinan
 */
@Service
@Reloadable(beanClass = InternalSshAuthService.class, initAfter = {InternalCentralConfigService.class},
        listenOn = CentralConfigKey.security)
public class SshAuthServiceImpl implements InternalSshAuthService {

    private static final String PUBLIC_KEY_FILE_NAME = "artifactory.ssh.public";
    private static final String PRIVATE_KEY_FILE_NAME = "artifactory.ssh.private";
    private static final Logger log = LoggerFactory.getLogger(SshAuthServiceImpl.class);

    @Autowired
    CentralConfigService centralConfigService;

    @Autowired
    UserGroupStoreService userGroupStoreService;

    private SshServer server;

    @Override
    public void init() {
        configureAndStartServer();
    }

    @Override
    public void reload(CentralConfigDescriptor oldDescriptor, List<DataDiff<?>> configDiff) {
        stopServer();
        configureAndStartServer();
    }

    @Override
    public void destroy() {
        stopServer();
    }

    @Override
    public boolean hasPublicKey() {
        return Files.exists(getPublicKeyFile());
    }

    @Override
    public boolean hasPrivateKey() {
        return Files.exists(getPrivateKeyFile());
    }

    @Override
    public Path getPublicKeyFile() {
        return sshFolder().resolve(PUBLIC_KEY_FILE_NAME);
    }

    @Override
    public Path getPrivateKeyFile() {
        return sshFolder().resolve(PRIVATE_KEY_FILE_NAME);
    }

    @Override
    public void savePublicKey(String publicKey) throws IOException {
        createSshFolder();
        Path path = getPublicKeyFile();
        boolean exists = ensureKeyFileExist(path, false);
        Files.write(path, publicKey.getBytes());
        ensureKeyFilePermissions(path);
        propagateSshKeyChange(path.toFile(), exists ? FileEventType.MODIFY : FileEventType.CREATE, false);
        log.info("Successfully updated SSH public key");
    }

    private void ensureKeyFilePermissions(Path path) {
        try {
            SecurityFolderHelper.setPermissionsOnSecurityFile(path, PERMISSIONS_MODE_600);
        } catch (IOException e) {
            log.error("Failed to set permissions on generated key file {}. you should manually set the file's " +
                    "permissions to 600", path.toAbsolutePath());
        }
    }

    private void propagateSshKeyChange(File file, FileEventType eventType, boolean privateKey) {
        try {
            ContextHelper.get().getConfigurationManager()
                    .forceFileChanged(file, eventType);
        } catch (Exception e) {
            String keyType = (privateKey) ? "private" : "public";
            log.debug("Failed to propagate {} ssh change", keyType, e);
            log.warn("Failed to propagate {} ssh change to other cluster nodes: {}", keyType, e.getMessage());
        }
    }

    @Override
    public void savePrivateKey(String privateKey) throws IOException {
        createSshFolder();
        Path path = getPrivateKeyFile();
        boolean exists = ensureKeyFileExist(path, true);
        Files.write(path, privateKey.getBytes());
        ensureKeyFilePermissions(path);
        propagateSshKeyChange(path.toFile(), exists ? FileEventType.MODIFY : FileEventType.CREATE, true);
        log.info("Successfully updated SSH private key");
    }

    @Override
    public void removePublicKey() throws IOException {
        Path publicKeyFile = getPublicKeyFile();
        Files.delete(publicKeyFile);
        propagateSshKeyChange(publicKeyFile.toFile(), FileEventType.DELETE, false);
        log.info("SSH public key was deleted");
    }

    @Override
    public void removePrivateKey() throws IOException {
        Path privateKeyFile = getPrivateKeyFile();
        try {
            Files.deleteIfExists(privateKeyFile);
            propagateSshKeyChange(privateKeyFile.toFile(), FileEventType.DELETE, true);
        } catch (IOException e) {
            throw new IOException("Failed to delete SSH private key file", e);
        }
        log.info("SSH private key was deleted");
    }

    /**
     * create ssh folder and check folder permission
     */
    private void createSshFolder() throws IOException {
        Path securityFolder = sshFolder();
        if (Files.notExists(securityFolder)) {
            Files.createDirectory(securityFolder);
            SecurityFolderHelper.setPermissionsOnSecurityFolder(securityFolder);
        }
        SecurityFolderHelper.checkPermissionsOnSecurityFolder(securityFolder);
    }

    /**
     * configure and start ssh server
     */
    private void configureAndStartServer() {
        if (centralConfigService != null && centralConfigService.getDescriptor() != null) {
            SecurityDescriptor securityDescriptor = centralConfigService.getDescriptor().getSecurity();
            SshServerSettings sshServerSettings = securityDescriptor.getSshServerSettings();
            if (sshServerSettings != null) {
                if (!sshServerSettings.isEnableSshServer()) {
                    return;
                }
                configServer();
                try {
                    server.start();
                } catch (IOException e) {
                    log.error("Failed to start SSH server", e);
                }
            }
        }
    }

    /**
     * stop ssh server
     */
    private void stopServer() {
        if (server == null) {
            return;
        }
        try {
            server.stop(true);
            while (!server.isClosed() && server.isClosing()) {
                Thread.sleep(10);
            }
        } catch (InterruptedException | IOException e) {
            log.error("Failed to stop SSH server", e);
        }
    }

    /**
     * configure ssh server
     */
    private void configServer() {
        server = ServerBuilder.builder()
                .cipherFactories(new CipherAlgorithms().setUpAlgorithms())
                .keyExchangeFactories(new KeyExchangeAlgorithms().setUpAlgorithms())
                .macFactories(new MacAlgorithms().setUpAlgorithms())
                .signatureFactories(new SignatureAlgorithms().setUpAlgorithms())
                .build();
        SshServerSettings sshServerSettings = centralConfigService.getDescriptor().getSecurity().getSshServerSettings();
        server.setPort(sshServerSettings.getSshServerPort());

        Path[] keys = new Path[] {getPrivateKeyFile(), getPublicKeyFile()};
        server.setKeyPairProvider(new FileKeyPairProvider(keys));
        // Capture Artifactory's per-thread state on this (Spring) thread so MINA SSHD worker threads can rebind them.
        ArtifactoryHome home = ArtifactoryHome.get();
        ArtifactoryContext context = ContextHelper.get();
        server.setPublickeyAuthenticator(new PublicKeyAuthenticator(userGroupStoreService, home, context));
        ArtifactoryCommandFactory commandFactory = new ArtifactoryCommandFactory(centralConfigService,
                userGroupStoreService, home, context);
        server.setCommandFactory(commandFactory);
    }

    /**
     * Ensure key file exists, and create it if not
     */
    private boolean ensureKeyFileExist(Path path, boolean privateKey) throws IOException {
        String keyType = privateKey ? "private" : "public";
        if (Files.notExists(path)) {
            try {
                Files.createFile(path);
                return false;
            } catch (IOException e) {
                String message = "Failed creating " + keyType + " key file: " + path.toAbsolutePath();
                log.debug(message, e);
                throw new IOException(message);
            }
        }
        return true;
    }

    @Override
    public void convert(CompoundVersionDetails source, CompoundVersionDetails target) {
        // nop
    }

    private Path sshFolder() {
        return ArtifactoryHome.get().getSecurityDir().toPath();
    }

}
