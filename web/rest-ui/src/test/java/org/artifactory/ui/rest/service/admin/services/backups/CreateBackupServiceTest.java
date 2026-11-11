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

package org.artifactory.ui.rest.service.admin.services.backups;

import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpStatus;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.backup.BackupDescriptor;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.util.AolUtils;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.nio.file.Files;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Verifies that {@link CreateBackupService} rejects a {@link BackupDescriptor} whose destination
 * directory violates the same gates the runtime applies — Artifactory home and the optional
 * backup allowlist — so a misconfiguration cannot be saved and only fail at the next cron tick.
 *
 * @author oleksandrl
 */
public class CreateBackupServiceTest extends ArtifactoryHomeBoundTest {

    private static final String BACKUP_KEY = "test-backup";

    @Mock
    private ArtifactoryRestRequest request;

    @Mock
    private CentralConfigService centralConfigService;

    @Mock
    private MutableCentralConfigDescriptor mutableDescriptor;

    private CreateBackupService createBackupService;
    private ArtifactoryRestResponse response;
    private AutoCloseable autoCloseable;

    @BeforeMethod
    public void setup() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        createBackupService = new CreateBackupService();
        ReflectionTestUtils.setField(createBackupService, "centralConfigService", centralConfigService);
        when(centralConfigService.getMutableDescriptor()).thenReturn(mutableDescriptor);
        response = new ArtifactoryRestResponse();
    }

    @AfterMethod
    public void tearDown() throws Exception {
        autoCloseable.close();
    }

    @Test
    public void testCreateRejectsDirInsideArtifactoryHome() {
        File homeDir = getOrCreateArtifactoryHomeStub().getHomeDir().getAbsoluteFile();
        File backupDir = new File(homeDir, "rogue-backup");
        BackupDescriptor descriptor = newDescriptor(backupDir);
        when(request.getImodel()).thenReturn(descriptor);

        executeWithMockedAol();

        assertEquals(response.getResponseCode(), HttpStatus.SC_BAD_REQUEST);
        assertEquals(response.getEntity(),
                String.format("{\"error\":\"Creating a backup inside '%s' is not allowed.\"}", backupDir));
        verify(centralConfigService, never()).saveEditedDescriptorAndReload(any(), any());
        verify(mutableDescriptor, never()).addBackup(any());
    }

    @Test
    public void testCreateRejectsDirOutsideAllowlist() throws Exception {
        File allowedRoot = Files.createTempDirectory("create-backup-allowlist").toFile();
        File requestedDir = Files.createTempDirectory("create-backup-outside").toFile();
        try {
            ArtifactoryHome.get().getArtifactoryProperties()
                    .setProperty(ConstantValues.backupAllowedPaths.getPropertyName(), allowedRoot.getAbsolutePath());
            BackupDescriptor descriptor = newDescriptor(requestedDir);
            when(request.getImodel()).thenReturn(descriptor);

            executeWithMockedAol();

            assertEquals(response.getResponseCode(), HttpStatus.SC_BAD_REQUEST);
            assertTrue(response.getEntity().toString()
                            .contains(BackupDescriptor.BACKUP_PATH_NOT_IN_ALLOWLIST),
                    "Expected allowlist error in response entity, got: " + response.getEntity());
            verify(centralConfigService, never()).saveEditedDescriptorAndReload(any(), any());
            verify(mutableDescriptor, never()).addBackup(any());
        } finally {
            ArtifactoryHome.get().getArtifactoryProperties()
                    .setProperty(ConstantValues.backupAllowedPaths.getPropertyName(), "");
            FileUtils.deleteQuietly(allowedRoot);
            FileUtils.deleteQuietly(requestedDir);
        }
    }

    @Test
    public void testCreateAllowsDirInsideAllowlist() throws Exception {
        File allowedRoot = Files.createTempDirectory("create-backup-inside").toFile();
        try {
            ArtifactoryHome.get().getArtifactoryProperties()
                    .setProperty(ConstantValues.backupAllowedPaths.getPropertyName(), allowedRoot.getAbsolutePath());
            File backupDir = new File(allowedRoot, "nested");
            BackupDescriptor descriptor = newDescriptor(backupDir);
            when(request.getImodel()).thenReturn(descriptor);

            executeWithMockedAol();

            assertEquals(response.getResponseCode(), HttpServletResponse.SC_CREATED);
            verify(mutableDescriptor).addBackup(descriptor);
            verify(centralConfigService).saveEditedDescriptorAndReload(mutableDescriptor, descriptor);
        } finally {
            ArtifactoryHome.get().getArtifactoryProperties()
                    .setProperty(ConstantValues.backupAllowedPaths.getPropertyName(), "");
            FileUtils.deleteQuietly(allowedRoot);
        }
    }

    @Test
    public void testCreateWithoutAllowlistConfiguredPreservesLegacyBehavior() throws Exception {
        File backupDir = Files.createTempDirectory("create-backup-legacy").toFile();
        try {
            BackupDescriptor descriptor = newDescriptor(backupDir);
            when(request.getImodel()).thenReturn(descriptor);

            executeWithMockedAol();

            assertEquals(response.getResponseCode(), HttpServletResponse.SC_CREATED);
            verify(mutableDescriptor).addBackup(descriptor);
            verify(centralConfigService).saveEditedDescriptorAndReload(mutableDescriptor, descriptor);
        } finally {
            FileUtils.deleteQuietly(backupDir);
        }
    }

    @Test
    public void testCreateAllowsNullDir() {
        BackupDescriptor descriptor = newDescriptor(null);
        when(request.getImodel()).thenReturn(descriptor);

        executeWithMockedAol();

        assertEquals(response.getResponseCode(), HttpServletResponse.SC_CREATED);
        verify(mutableDescriptor).addBackup(descriptor);
        verify(centralConfigService).saveEditedDescriptorAndReload(mutableDescriptor, descriptor);
    }

    private BackupDescriptor newDescriptor(File dir) {
        BackupDescriptor descriptor = new BackupDescriptor();
        descriptor.setKey(BACKUP_KEY);
        descriptor.setDir(dir);
        return descriptor;
    }

    private void executeWithMockedAol() {
        try (MockedStatic<AolUtils> mockedAolUtils = Mockito.mockStatic(AolUtils.class)) {
            mockedAolUtils.when(() -> AolUtils.assertNotAol(anyString())).thenAnswer(invocation -> null);
            createBackupService.execute(request, response);
        }
    }
}
