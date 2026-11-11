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

package org.artifactory.repo.service.trash;

import com.google.common.collect.Maps;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.interceptor.storage.StorageInterceptorsImpl;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.sapi.interceptor.StorageInterceptor;
import org.artifactory.sapi.interceptor.context.BulkCopyMoveContext;
import org.artifactory.sapi.interceptor.context.DeleteContext;
import org.artifactory.sapi.interceptor.context.InterceptorCreateContext;
import org.artifactory.sapi.interceptor.context.InterceptorMoveCopyContext;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.mockito.Mockito;
import org.mockito.stubbing.Stubber;
import org.opentest4j.AssertionFailedError;
import org.testng.annotations.Test;
import org.testng.collections.Lists;

import java.util.Map;

import static org.artifactory.repo.system.SystemRepos.TRASH_REPO_KEY;

/**
 * Test that storage interceptors are not triggered on the trash
 *
 * @author Shay Yaakov
 */
public class TrashSkipInterceptorsTest {

    @Test
    public void testInterceptors() throws Exception {
        StorageInterceptor root = Mockito.mock(StorageInterceptor.class);
        StorageInterceptor interceptor = prepareMockInterceptor();

        Map<String, StorageInterceptor> interceptorsHashMap = Maps.newHashMap();
        interceptorsHashMap.put("root", root);
        interceptorsHashMap.put("skipper", interceptor);

        ArtifactoryApplicationContext context = Mockito.mock(ArtifactoryApplicationContext.class);
        Mockito.when(context.beansForType(StorageInterceptor.class)).thenReturn(interceptorsHashMap);
        Mockito.when(context.getBean("root")).thenReturn(root);

        VfsItem vfsItem = Mockito.mock(VfsItem.class);
        RepoPath repoPath = RepoPathFactory.create(TRASH_REPO_KEY, "some/path");
        Mockito.when(vfsItem.getRepoPath()).thenReturn(repoPath);

        StorageInterceptorsImpl interceptors = new StorageInterceptorsImpl();
        interceptors.setApplicationContext(context);
        interceptors.setBeanName("root");
        interceptors.init();
        interceptors.beforeCreate(vfsItem, null);
        interceptors.afterCreate(vfsItem, null, new InterceptorCreateContext());
        interceptors.beforeDelete(vfsItem, null, null);
        interceptors.afterDelete(vfsItem, null, new DeleteContext(vfsItem.getRepoPath()));
        interceptors.beforeCopyFiles(new BulkCopyMoveContext(Lists.newArrayList(repoPath), null));
        interceptors.afterCopy(null, vfsItem, null, null, null);
        interceptors.beforeMove(vfsItem, null, null, null);
        interceptors.beforePropertyCreate(vfsItem, null, null);
        interceptors.afterPropertyCreate(vfsItem, null, null);
        interceptors.beforePropertyDelete(vfsItem, null, null);
        interceptors.afterPropertyDelete(vfsItem, null, null);

        Mockito.verify(context).beansForType(StorageInterceptor.class);
        Mockito.verify(context).getBean("root");
    }


    private StorageInterceptor prepareMockInterceptor() {
        StorageInterceptor interceptor = Mockito.mock(StorageInterceptor.class);

        failOnMethodCall().when(interceptor)
                .beforeCreate(Mockito.any(VfsItem.class), Mockito.any(MutableStatusHolder.class));
        failOnMethodCall().when(interceptor)
                .afterCreate(Mockito.any(VfsItem.class), Mockito.any(MutableStatusHolder.class),
                        Mockito.any(InterceptorCreateContext.class));
        failOnMethodCall().when(interceptor)
                .beforeDelete(Mockito.any(VfsItem.class), Mockito.any(MutableStatusHolder.class),
                        Mockito.any(DeleteContext.class));
        failOnMethodCall().when(interceptor)
                .afterDelete(Mockito.any(VfsItem.class), Mockito.any(MutableStatusHolder.class),
                        Mockito.any(DeleteContext.class));
        failOnMethodCall().when(interceptor).beforeCopyFiles(Mockito.any());
        failOnMethodCall().when(interceptor).afterCopy(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
                Mockito.any(InterceptorMoveCopyContext.class));
        failOnMethodCall().when(interceptor).beforeMove(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        failOnMethodCall().when(interceptor).afterMove(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
                Mockito.any(InterceptorMoveCopyContext.class));
        failOnMethodCall().when(interceptor).beforePropertyCreate(Mockito.any(), Mockito.any(), Mockito.any());
        failOnMethodCall().when(interceptor).afterPropertyCreate(Mockito.any(), Mockito.any(), Mockito.any());
        failOnMethodCall().when(interceptor).beforePropertyDelete(Mockito.any(), Mockito.any(), Mockito.any());
        failOnMethodCall().when(interceptor).afterPropertyDelete(Mockito.any(), Mockito.any(), Mockito.any());

        return interceptor;
    }

    private Stubber failOnMethodCall() {
        return Mockito.doThrow(new AssertionFailedError("Interceptor should not get called on trash"));
    }

}