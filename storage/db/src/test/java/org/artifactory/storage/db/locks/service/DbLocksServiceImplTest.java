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

package org.artifactory.storage.db.locks.service;

import com.google.common.collect.Sets;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.storage.db.dao.locks.DbDistributeLocksDao;
import org.artifactory.storage.db.dao.locks.LockInfo;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.artifactory.concurrent.NonStandardLockCategory.CLUSTER_SINGLETON;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * @author Shay Bagants
 * @author Mor Merhav
 */
public class DbLocksServiceImplTest extends ArtifactoryHomeBoundTest {

    @Captor
    private ArgumentCaptor<String> categoryArgumentCaptor;
    @Captor
    private ArgumentCaptor<String> keyArgumentCaptor;

    private DbDistributeLocksDao lockDaoMock;
    private DbLocksServiceImpl dbLocksService;
    private AutoCloseable mocks;

    @BeforeMethod
    public void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        lockDaoMock = mock(DbDistributeLocksDao.class);
        when(context.getServerId()).thenReturn("server");
        ArtifactoryContextThreadBinder.bind(context);
        dbLocksService = new DbLocksServiceImpl(lockDaoMock, new ArtifactoryDbProperties(ArtifactoryHome.get()));
    }

    @AfterMethod
    public void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    public void testLockingMapSize() throws SQLException {
        when(lockDaoMock.lockingMapSize(anyString())).thenReturn(3);
        int myCategory = dbLocksService.lockingMapSize("myCategory");
        Assert.assertEquals(myCategory, 3);
        verify(lockDaoMock).lockingMapSize(any());
    }

    @Test(expectedExceptions = IllegalStateException.class, description = "Usually such tests should be avoided, but " +
            "in this case its quite important that we don't swallow SQL exception in cases we don't need to")
    public void testSqlExceptionNotSwallowed() throws SQLException, TimeoutException {
        Connection connection = mock(Connection.class);
        when(lockDaoMock.tryToAcquireLock(any())).thenThrow(new SQLException("bam!"));
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(metadata.getConnection()).thenReturn(connection);
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(true);
        when(metadata.getTables(any(), any(), any(), any())).thenReturn(resultSet);
        when(connection.getMetaData()).thenReturn(metadata);
        when(connection.getSchema()).thenReturn("foo");
        when(lockDaoMock.getLocksConnection()).thenReturn(connection);

        dbLocksService.acquireLock("category", "key", "owner", 1L, TimeUnit.SECONDS);
    }

    @Test(expectedExceptions = RuntimeException.class, expectedExceptionsMessageRegExp = "Failed to query out number " +
            "of locks for.*\\. my reason.*")
    public void testLockingMapSizeErrorMessage() throws SQLException {
        when(lockDaoMock.lockingMapSize("myCategory")).thenThrow(new SQLException("my reason"));
        dbLocksService.lockingMapSize("myCategory");
    }

    @Test
    public void testReleaseOrphanLocksByCategoryExcludingServersEmptyExcludedServerIds() {
        dbLocksService.releaseOrphanLocksByCategoryExcludingServers(Sets.newHashSet(), "category");
        verifyNoInteractions(lockDaoMock);
    }

    @Test
    public void testReleaseOrphanLocksByCategoryExcludingServersNullExcludedServerIds() {
        dbLocksService.releaseOrphanLocksByCategoryExcludingServers(null, "category");
        verifyNoInteractions(lockDaoMock);
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "Lock category " +
            "cannot be empty")
    public void testReleaseOrphanLocksByCategoryExcludingServersEmptyCategory() {
        dbLocksService.releaseOrphanLocksByCategoryExcludingServers(Sets.newHashSet("owner"), StringUtils.EMPTY);
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "Lock category " +
            "cannot be empty")
    public void testReleaseOrphanLocksByCategoryExcludingServersNullCategory() {
        dbLocksService.releaseOrphanLocksByCategoryExcludingServers(Sets.newHashSet("owner"), null);
    }

    @Test
    public void testReleaseOrphanLocksByCategoryExcludingServers() throws SQLException {
        Set<String> serverIds = Sets.newHashSet("owner");
        LockInfo lockInfo = new LockInfo("category", "key", "owner", 1L, "thread", 111);
        Set<LockInfo> lockInfoSet = Sets.newHashSet(lockInfo);
        when(lockDaoMock.getAllLocksByCategoryNotOwnedBy(serverIds, "category")).thenReturn(lockInfoSet);
        when(lockDaoMock.deleteLockByCategoryAndKey("category", "key")).thenReturn(true);

        dbLocksService.releaseOrphanLocksByCategoryExcludingServers(serverIds, "category");

        verify(lockDaoMock).deleteLockByCategoryAndKey("category", "key");
    }

    @Test
    public void testReleaseOrphanLocksExcludingServersAndCategoriesEmptyExcludedServerIds() {
        dbLocksService.releaseOrphanLocksExcludingServersAndCategories(Sets.newHashSet(), Sets.newHashSet("category"));
        verify(lockDaoMock, times(0)).getAllLocksNotOwnedByAndNotContainedInCategories(any(), any());
        dbLocksService.releaseOrphanLocksExcludingServersAndCategories(null, Sets.newHashSet("category"));
        verify(lockDaoMock, times(0)).getAllLocksNotOwnedByAndNotContainedInCategories(any(), any());
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "Categories excluded" +
            " from lock release must not be empty")
    public void testReleaseOrphanLocksExcludingServersAndCategoriesEmptyCategorySet() {
        dbLocksService.releaseOrphanLocksExcludingServersAndCategories(Sets.newHashSet("owner"), Sets.newHashSet());
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "Categories excluded" +
            " from lock release must not be empty")
    public void testReleaseOrphanLocksExcludingServersAndCategoriesNullCategory() {
        dbLocksService.releaseOrphanLocksExcludingServersAndCategories(Sets.newHashSet("owner"), null);
    }

    @Test
    public void testReleaseOrphanLocksExcludingServersAndCategories() throws SQLException {
        Set<String> serverIds = Sets.newHashSet("owner1");
        Set<String> categories = Sets.newHashSet("category1");
        LockInfo lockInfo = new LockInfo("category2", "key", "owner2", 1L, "thread", 111);
        Set<LockInfo> lockInfoSet = Sets.newHashSet(lockInfo);
        when(lockDaoMock.getAllLocksNotOwnedByAndNotContainedInCategories(serverIds, categories))
                .thenReturn(lockInfoSet);
        when(lockDaoMock.deleteLockByCategoryAndKey("category2", "key")).thenReturn(true);

        dbLocksService.releaseOrphanLocksExcludingServersAndCategories(serverIds, categories);

        verify(lockDaoMock).deleteLockByCategoryAndKey("category2", "key");
    }

    @Test
    public void testReleaseAllLocksByOwnerExcludingKeys() throws SQLException {
        Set<String> excludedKeys = Sets.newHashSet("excludedKey");
        String owner = "owner";

        LockInfo lockInfo1 = new LockInfo("category", "key", owner, 1L, "thread", 111);
        LockInfo lockInfo2 = new LockInfo("category", excludedKeys.iterator().next(), owner, 1L, "thread", 111);
        Set<LockInfo> lockInfoSet = Sets.newHashSet(lockInfo1, lockInfo2);
        when(lockDaoMock.getAllLocksByOwner(eq(owner))).thenReturn(lockInfoSet);
        when(lockDaoMock.deleteLockByCategoryAndKey("category", "key")).thenReturn(true);

        dbLocksService.releaseAllLocksByOwnerExcludingKeys(owner, excludedKeys);

        verify(lockDaoMock).deleteLockByCategoryAndKey("category", "key");
    }

    @Test
    public void testIsLockedByPrefixExcluding() {
        List<LockInfo> lockInfoList = new ArrayList<>();
        String prefix = "_prefix";
        lockInfoList.add(new LockInfo(null, prefix + "1", null, 100000, null, 100000));
        lockInfoList.add(new LockInfo(null, prefix + "2", null, 100000, null, 100000));
        when(lockDaoMock.getLocksByPrefix(CLUSTER_SINGLETON.getName(), "_prefix", 2))
                .thenReturn(lockInfoList);
        assertTrue(dbLocksService.isLockedByPrefixExcluding(CLUSTER_SINGLETON.getName(), prefix, prefix + "1", false));
        lockInfoList.remove(1);
        assertFalse(dbLocksService.isLockedByPrefixExcluding(CLUSTER_SINGLETON.getName(), prefix, prefix + "1", false
        ));
    }

    @Test
    @SneakyThrows
    public void unlockWithStuckSameKeysThenForceUnlockAllStuckExpectCallToForceUnlock() {
        dbLocksService =
                Mockito.spy(new DbLocksServiceImpl(lockDaoMock, new ArtifactoryDbProperties(ArtifactoryHome.get())));
        when(lockDaoMock.unlock(any(), any(), any())).thenThrow(new SQLException());
        try {
            dbLocksService.unlock("category1", "key1", "owner");
        } catch (IllegalStateException illegalStateException) {
            try {
                dbLocksService.unlock("category1", "key2", "owner");
            } catch (Exception ex) {
                dbLocksService.forceUnlockAllStuck();
                verify(dbLocksService, times(2))
                        .forceUnlock(categoryArgumentCaptor.capture(), keyArgumentCaptor.capture());
                List<String> actualKeys = keyArgumentCaptor.getAllValues();
                List<String> actualCategories = categoryArgumentCaptor.getAllValues();
                assertEquals(actualKeys.get(0), "key2");
                assertEquals(actualKeys.get(1), "key1");
                assertEquals(actualCategories.get(0), "category1");
                assertEquals(actualCategories.get(1), "category1");
            }
        }
    }

    @Test
    @SneakyThrows
    public void unlockWithStuckSameKeysThenForceUnlockAllStuckExpectCallToForceUnlockNotAllClear() {
        dbLocksService =
                Mockito.spy(new DbLocksServiceImpl(lockDaoMock, new ArtifactoryDbProperties(ArtifactoryHome.get())));
        when(lockDaoMock.unlock(any(), any(), any())).thenThrow(new SQLException());
        try {
            dbLocksService.unlock("category1", "key1", "owner");
        } catch (IllegalStateException illegalStateException) {
            try {
                dbLocksService.unlock("category1", "key2", "owner");
            } catch (Exception ex) {
                doReturn(true, false).when(dbLocksService)
                        .forceUnlock("category1", "key1");
                dbLocksService.forceUnlockAllStuck();
                verify(dbLocksService, times(2))
                        .forceUnlock(categoryArgumentCaptor.capture(), keyArgumentCaptor.capture());
                dbLocksService.forceUnlockAllStuck();
                verify(dbLocksService, times(3))
                        .forceUnlock(categoryArgumentCaptor.capture(), keyArgumentCaptor.capture());
            }
        }
    }

    @Test
    @SneakyThrows
    public void unlockWithStuckThenForceUnlockAllStuckWithExceptionExpectNoFail() {
        dbLocksService =
                Mockito.spy(new DbLocksServiceImpl(lockDaoMock, new ArtifactoryDbProperties(ArtifactoryHome.get())));
        when(lockDaoMock.unlock(any(), any(), any())).thenThrow(new SQLException());
        Mockito.doThrow(new IllegalStateException()).when(dbLocksService).forceUnlock(anyString(), anyString());
        try {
            dbLocksService.unlock("category1", "key1", "owner");
        } catch (IllegalStateException ise) {
            try {
                dbLocksService.unlock("category2", "key2", "owner");
            } catch (Exception ex) {
                try {
                    dbLocksService.forceUnlockAllStuck();
                } catch (Exception e) {
                    fail();
                }
            }
        }
        verify(dbLocksService, times(2))
                .forceUnlock(anyString(), anyString());
        dbLocksService.forceUnlockAllStuck();
        verify(dbLocksService, times(4))
                .forceUnlock(anyString(), anyString());
    }
}