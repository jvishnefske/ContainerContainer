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

package org.artifactory.repo.interceptor;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.NuGetAddon;
import org.artifactory.addon.nuget.NuGetProperties;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.fs.FileInfo;
import org.artifactory.md.Properties;
import org.artifactory.metadata.service.indexer.MetadataServiceIndexHandler;
import org.artifactory.properties.PropertiesImpl;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.sapi.fs.VfsFile;
import org.artifactory.sapi.interceptor.context.InterceptorCreateContext;
import org.artifactory.sapi.interceptor.context.InterceptorMoveCopyContext;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.junit.jupiter.api.Assertions;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

/**
 * @author Noam Y. Tenne
 */
@Test
public class NuGetCalculationInterceptorTest extends ArtifactoryHomeBoundTest {
    private static final String REPO_KEY = "nuget-local";
    private static final String SYMBOL_FILENAME =
            "nuget-local/Ipreo.AutomationFramework.UI.Win32.1.0.0.2045.symbols.nupkg";

    private NuGetCalculationInterceptor nuGetCalculationInterceptor = new NuGetCalculationInterceptor();
    private NuGetAddon nuGetAddon;
    private MetadataServiceIndexHandler metadataServiceIndexHandler;

    @BeforeClass
    public void setUp() {
    }

    @BeforeMethod
    public void beforeMethod() {
        nuGetCalculationInterceptor.addonsManager = createMock(AddonsManager.class);
        nuGetAddon = createMock(NuGetAddon.class);
        metadataServiceIndexHandler = createMock(MetadataServiceIndexHandler.class);
        expect(nuGetCalculationInterceptor.addonsManager.addonByType(NuGetAddon.class))
                .andReturn(nuGetAddon).anyTimes();
        nuGetCalculationInterceptor.metadataServiceIndexHandler = metadataServiceIndexHandler;
    }

    @Test
    public void testNotNuGetFile() {
        VfsFile vfsItemMock = createAndGetFsItemMock(RepoPathFactory.create(REPO_KEY, "bob.jar"));
        replay(vfsItemMock);

        nuGetCalculationInterceptor.afterCopy(null, vfsItemMock, null, null, null);
        nuGetCalculationInterceptor.afterCreate(vfsItemMock, null, new InterceptorCreateContext());
        nuGetCalculationInterceptor.afterMove(null, vfsItemMock, null, null, null);
        nuGetCalculationInterceptor.beforeDelete(vfsItemMock, null, null);

        verify(vfsItemMock);
    }

    @Test
    public void testNotNuGetRepo() {
        RepositoryService repoServiceMock = createAndSetMockRepo(false, REPO_KEY);

        VfsFile vfsItemMock = createAndGetFsItemMock(RepoPathFactory.create(REPO_KEY, "bob.nupkg"));
        replay(vfsItemMock, repoServiceMock);

        nuGetCalculationInterceptor.afterCopy(null, vfsItemMock, null, null, null);
        nuGetCalculationInterceptor.afterCreate(vfsItemMock, null, new InterceptorCreateContext());
        nuGetCalculationInterceptor.afterMove(null, vfsItemMock, null, null, null);
        nuGetCalculationInterceptor.beforeDelete(vfsItemMock, null, null);

        verify(vfsItemMock, repoServiceMock);
    }

    @Test
    public void testAfterCreate() {
        RepositoryService repoServiceMock = createAndSetMockRepo(true, REPO_KEY);
        RepoPath repoPath = RepoPathFactory.create(REPO_KEY, "bob.nupkg");
        VfsFile vfsItemMock = createAndGetFsItemMock(repoPath);
        FileInfo fileInfoMock = createAndGetFileInfoMock(repoPath);
        expect(vfsItemMock.getInfo()).andReturn(fileInfoMock).anyTimes();
        nuGetAddon.extractNuPkgInfo(eq(fileInfoMock), eq(true));
        expectLastCall();
        replay(vfsItemMock, repoServiceMock, nuGetCalculationInterceptor.addonsManager, nuGetAddon);
        nuGetCalculationInterceptor.afterCreate(vfsItemMock, new BasicStatusHolder(), new InterceptorCreateContext());
        verify(vfsItemMock, repoServiceMock, nuGetCalculationInterceptor.addonsManager, nuGetAddon);
    }

    /**
     * This test expects to execute symbol package specific logic only, not *.nupkg extraction.
     */
    @Test
    public void testAfterCreateForSymbolPackage() {
        RepositoryService repoServiceMock = createAndSetMockRepo(true, REPO_KEY);
        RepoPath repoPath = RepoPathFactory.create(REPO_KEY, SYMBOL_FILENAME);
        VfsFile vfsItemMock = createAndGetFsItemMock(repoPath);
        FileInfo fileInfoMock = createAndGetFileInfoMock(repoPath);
        expect(vfsItemMock.getInfo()).andReturn(fileInfoMock).anyTimes();
        nuGetAddon.extractSNuPkgInfo(eq(fileInfoMock));
        expectLastCall();
        replay(vfsItemMock, repoServiceMock, nuGetCalculationInterceptor.addonsManager, nuGetAddon);
        nuGetCalculationInterceptor.afterCreate(vfsItemMock, new BasicStatusHolder(), new InterceptorCreateContext());
        verify(vfsItemMock, repoServiceMock, nuGetCalculationInterceptor.addonsManager, nuGetAddon);
    }

    @Test
    public void testAfterCopyForSymbolPackage() {
        RepositoryService repoServiceMock = createAndSetMockRepo(true, REPO_KEY);
        RepoPath repoPath = RepoPathFactory.create(REPO_KEY, SYMBOL_FILENAME);
        VfsFile vfsItemMock = createAndGetFsItemMock(repoPath);
        FileInfo fileInfoMock = createAndGetFileInfoMock(repoPath);
        expect(vfsItemMock.getInfo()).andReturn(fileInfoMock).anyTimes();
        nuGetAddon.extractSNuPkgInfo(eq(fileInfoMock));
        expectLastCall();
        replay(vfsItemMock, repoServiceMock, nuGetCalculationInterceptor.addonsManager, nuGetAddon);
        nuGetCalculationInterceptor.afterCopy(vfsItemMock, vfsItemMock, new BasicStatusHolder(), new PropertiesImpl(),
                new InterceptorMoveCopyContext());
        verify(vfsItemMock, repoServiceMock, nuGetCalculationInterceptor.addonsManager, nuGetAddon);
    }

    @Test
    public void testAfterMoveForSymbolPackage() {
        RepositoryService repoServiceMock = createAndSetMockRepo(true, REPO_KEY);
        RepoPath repoPath = RepoPathFactory.create(REPO_KEY, SYMBOL_FILENAME);
        VfsFile vfsItemMock = createAndGetFsItemMock(repoPath);
        FileInfo fileInfoMock = createAndGetFileInfoMock(repoPath);
        expect(vfsItemMock.getInfo()).andReturn(fileInfoMock).anyTimes();
        nuGetAddon.extractSNuPkgInfo(eq(fileInfoMock));
        expectLastCall();
        replay(vfsItemMock, repoServiceMock, nuGetCalculationInterceptor.addonsManager, nuGetAddon);
        nuGetCalculationInterceptor.afterMove(vfsItemMock, vfsItemMock, new BasicStatusHolder(), new PropertiesImpl(),
                new InterceptorMoveCopyContext());
        verify(vfsItemMock, repoServiceMock, nuGetCalculationInterceptor.addonsManager, nuGetAddon);
    }

    @Test(dataProvider = "shouldTakeActionDataProvider")
    public void testShouldTakeAction(String path, boolean expected) {
        RepositoryService repoServiceMock = createAndSetMockRepo(true, REPO_KEY);
        RepoPath repoPath = RepoPathFactory.create(REPO_KEY, path);
        VfsFile vfsItemMock = createAndGetFsItemMock(repoPath);
        replay(vfsItemMock, repoServiceMock);
        Assertions.assertEquals(expected, nuGetCalculationInterceptor.shouldTakeAction(vfsItemMock));
    }

    @Test(dataProvider = "symbolPackagePaths")
    public void testIsSymbolFile(String path, boolean expected) {
        Assertions.assertEquals(expected, nuGetCalculationInterceptor.isSymbolPackage(path));
    }

    @DataProvider
    private Object[][] symbolPackagePaths() {
        return new Object[][] {
                {SYMBOL_FILENAME, true},
                {"nuget-local/Ipreo.AutomationFramework.UI.Win32.1.0.0.2045.nupkg", false},
                {"bob.nupkg", false},
                {"bob.jar", false},
                {"bob.snupkg", true}
        };
    }

    @DataProvider
    private Object[][] shouldTakeActionDataProvider() {
        return new Object[][] {
                {"bob.nupkg", true},
                {"bob.jar", false},
                {SYMBOL_FILENAME, false},
                {"bob.snupkg", false}
        };
    }

    @Test
    public void testAfterMoveOrCopyNoIdProperty() {
        RepositoryService repoServiceMock = createAndSetMockRepo(true, "bob");
        RepoPath itemRepoPath = RepoPathFactory.create("bob", "bob.nupkg");
        FileInfo fileInfoMock = createAndGetFileInfoMock(itemRepoPath);
        VfsFile vfsItemMock = createAndGetFsItemMock(itemRepoPath);
        expect(vfsItemMock.getInfo()).andReturn(fileInfoMock).anyTimes();
        setVfsFileMockProperties(vfsItemMock, null);

        replay(vfsItemMock, repoServiceMock, nuGetCalculationInterceptor.addonsManager);
        nuGetCalculationInterceptor.afterCopy(null, vfsItemMock, null, null, null);
        nuGetCalculationInterceptor.afterMove(null, vfsItemMock, null, null, null);
        verify(vfsItemMock, repoServiceMock, nuGetCalculationInterceptor.addonsManager);
    }

    @Test
    public void testAfterCopy() {
        RepositoryService repoServiceMock = createAndSetMockRepo(true, "bob");
        RepoPath itemRepoPath = RepoPathFactory.create("bob", "bob.nupkg");
        FileInfo fileInfoMock = createAndGetFileInfoMock(itemRepoPath);
        VfsFile vfsItemMock = createAndGetFsItemMock(itemRepoPath);
        expect(vfsItemMock.getInfo()).andReturn(fileInfoMock).anyTimes();
        setVfsFileMockProperties(vfsItemMock, "bobsId");

        replay(vfsItemMock, repoServiceMock, nuGetCalculationInterceptor.addonsManager);
        nuGetCalculationInterceptor.afterCopy(null, vfsItemMock, null, null, null);
        verify(vfsItemMock, repoServiceMock, nuGetCalculationInterceptor.addonsManager);
    }

    @Test
    public void testAfterMoveNoNuGetProperties() {
        RepositoryService repoServiceMock = createAndSetMockRepo(true, "bob");
        RepoPath targetRepoPath = RepoPathFactory.create("bob", "bob.nupkg");
        FileInfo fileInfoMock = createAndGetFileInfoMock(targetRepoPath);
        VfsFile targetVfsFile = createAndGetFsItemMock(targetRepoPath);
        expect(targetVfsFile.getInfo()).andReturn(fileInfoMock).anyTimes();
        setVfsFileMockProperties(targetVfsFile, null);

        BasicStatusHolder statusHolder = new BasicStatusHolder();
        nuGetAddon.extractNuPkgInfo(eq(fileInfoMock), eq(statusHolder), eq(true));
        expectLastCall();

        replay(targetVfsFile, repoServiceMock, nuGetCalculationInterceptor.addonsManager);
        nuGetCalculationInterceptor.afterMove(targetVfsFile, targetVfsFile, statusHolder, null, null);
        verify(targetVfsFile, repoServiceMock, nuGetCalculationInterceptor.addonsManager);
    }

    @Test
    public void testAfterImport() {
        RepositoryService repoServiceMock = createAndSetMockRepo(true, REPO_KEY);
        RepoPath itemRepoPath = RepoPathFactory.create(REPO_KEY, "bob.nupkg");
        FileInfo fileInfoMock = createAndGetFileInfoMock(itemRepoPath);
        VfsFile vfsItemMock = createAndGetFsItemMock(itemRepoPath);
        expect(vfsItemMock.getInfo()).andReturn(fileInfoMock).anyTimes();
        BasicStatusHolder statusHolder = new BasicStatusHolder();

        nuGetAddon.extractNuPkgInfo(eq(fileInfoMock), eq(true));
        expectLastCall();

        replay(vfsItemMock, repoServiceMock, nuGetCalculationInterceptor.addonsManager, nuGetAddon);
        nuGetCalculationInterceptor.afterImport(vfsItemMock, statusHolder);
        verify(vfsItemMock, repoServiceMock, nuGetCalculationInterceptor.addonsManager, nuGetAddon);
    }

    private VfsFile createAndGetFsItemMock(RepoPath repoPath) {
        VfsFile vfsItemMock = createMock(VfsFile.class);
        expect(vfsItemMock.isFile()).andReturn(true).anyTimes();
        expect(vfsItemMock.getRepoPath()).andReturn(repoPath).anyTimes();
        return vfsItemMock;
    }

    private FileInfo createAndGetFileInfoMock(RepoPath repoPath) {
        FileInfo fileInfoMock = createMock(FileInfo.class);
        expect(fileInfoMock.getRepoPath()).andReturn(repoPath).anyTimes();
        return fileInfoMock;
    }

    private RepositoryService createAndSetMockRepo(boolean nuGetSupported, String repoKey) {
        RepositoryService repoServiceMock = createMock(RepositoryService.class);
        LocalRepoDescriptor localRepoDescriptor = new LocalRepoDescriptor();
        if (nuGetSupported) {
            localRepoDescriptor.setType(RepoType.NuGet);
        }
        expect(repoServiceMock.repoDescriptorByKey(repoKey)).andReturn(localRepoDescriptor).anyTimes();
        nuGetCalculationInterceptor.repositoryService = repoServiceMock;
        return repoServiceMock;
    }

    private void setVfsFileMockProperties(VfsFile vfsFile, String nugetId) {
        Properties toReturn = new PropertiesImpl();
        if (!StringUtils.isBlank(nugetId)) {
            toReturn.put(NuGetProperties.Id.nodePropertyName(), nugetId);
        }

        expect(vfsFile.getProperties()).andReturn(toReturn).anyTimes();
    }
}
