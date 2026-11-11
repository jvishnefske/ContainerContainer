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

package org.artifactory.storage.jobs.migration.sha256;

import com.google.common.collect.Maps;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.exception.ItemNotFoundRuntimeException;
import org.artifactory.checksum.ChecksumInfo;
import org.artifactory.checksum.ChecksumType;
import org.artifactory.fs.MutableFileInfo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.service.RepositoryServiceImpl;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.artifactory.sh.acl.ArtifactoryBinaryService;
import org.artifactory.storage.db.fs.dao.NodesDao;
import org.artifactory.storage.fs.service.FileService;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.jfrog.storage.binstore.common.BinaryElementImpl;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.SQLException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * @author Uriah Levy
 */
public class Sha256MigrationJobTestBase extends ArtifactoryHomeBoundTest {

    @Mock
    private ArtifactoryApplicationContext artifactoryContext;
    @Mock
    InternalRepositoryService internalRepositoryService;
    @Mock
    private ArtifactoryBinaryService artifactoryBinaryService;
    @Mock
    private NodesDao nodesDao;
    @Mock
    public FileService fileService;
    @Mock
    private MutableFileInfo fileInfo;

    RepositoryServiceImpl repoService = new RepositoryServiceImpl();
    private AutoCloseable mocks;

    @BeforeMethod
    public void beforeMethod() {
        mocks = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        // Context
        mockServices();
    }

    @AfterMethod
    public void afterMethod() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    private void mockServices() {
        when(artifactoryContext.beanForType(ArtifactoryBinaryService.class)).thenReturn(artifactoryBinaryService);
        when(artifactoryContext.beanForType(InternalRepositoryService.class)).thenReturn(internalRepositoryService);
        when(artifactoryContext.beanForType(NodesDao.class)).thenReturn(nodesDao);
        ReflectionTestUtils.setField(repoService, "fileService", fileService);
    }

    /**
     * Dynamically init required method stubs per every test-case with different params
     */
    void initStubs(String sha1, String sha2, String md5, String repoKey, BinaryElementImpl binaryElement,
            RepoPath repoPath, Throwable exception) throws SQLException {
        services(sha1, sha2, md5, binaryElement);
        daos(sha1, sha2);
        // define stub exception behavior based on input
        exceptions(sha2, repoPath, exception);
    }

    private void exceptions(String sha2, RepoPath repoPath, Throwable exception) {
        if (exception.getClass().isAssignableFrom(ItemNotFoundRuntimeException.class)) {
            doThrow(exception)
                    .when(internalRepositoryService).updateSha2ForPath(repoPath, sha2);
            doNothing().when(internalRepositoryService).updateSha2ForPathNotFound(repoPath, sha2);
        } else if (exception.getClass().isAssignableFrom(RuntimeException.class)) {
            doThrow(exception)
                    .when(internalRepositoryService).updateSha2ForPath(repoPath, sha2);
        }
    }

    private void services(String sha1, String sha2, String md5, BinaryElementImpl binaryElement) throws SQLException {
        when(artifactoryBinaryService.findBinary(ChecksumType.sha1, sha1)).thenReturn(new BinaryElementImpl(sha1,
                null, md5, 10));
        when(artifactoryBinaryService.createBinaryElement(sha1, null, null, -1, Maps.newHashMap()))
                .thenReturn(binaryElement);
        when(artifactoryBinaryService.getBinary(binaryElement))
                .thenReturn(new ByteArrayInputStream(sha1.getBytes()));
        when(artifactoryBinaryService.updateSha2ForSha1(sha1, sha2)).thenReturn(true);
        when(fileService.loadItem(any(RepoPath.class))).thenReturn(fileInfo);
        doNothing().when(fileInfo).addChecksumInfo(any(ChecksumInfo.class));
    }

    private void daos(String sha1, String sha2) throws SQLException {
        when(nodesDao.getMissingSha2ArtifactCount()).thenReturn(1);
    }

    static int getErrorMapFieldSize(Sha256MigrationJob sha256MigrationJob, String fieldName)
            throws NoSuchFieldException,
            IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        Field noneFatalNodeErrorsField = sha256MigrationJob.getClass().getDeclaredField(fieldName);
        noneFatalNodeErrorsField.setAccessible(true);
        Object fieldValue = noneFatalNodeErrorsField.get(sha256MigrationJob);
        Method sizeMethod = fieldValue.getClass().getDeclaredMethod("size", (Class<?>[]) null);
        return (int) sizeMethod.invoke(fieldValue);
    }
}
