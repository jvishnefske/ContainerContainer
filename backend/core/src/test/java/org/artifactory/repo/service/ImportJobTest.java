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
package org.artifactory.repo.service;

import com.google.common.io.Files;
import org.artifactory.addon.release.bundle.ReleaseBundleAddon;
import org.artifactory.api.config.RepositoryImportSettingsImpl;
import org.artifactory.repo.db.importexport.DbRepoImportHandler;
import org.artifactory.sapi.common.ImportSettings;
import org.artifactory.storage.StorageService;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.impl.JobDetailImpl;
import org.springframework.core.task.AsyncTaskExecutor;
import org.testng.annotations.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Yoav Luft
 */
public class ImportJobTest {

    @Test
    void testFinalizeImportCalledOnlyAfterImport() {

        AsyncTaskExecutor asyncTaskExecutor = mock(AsyncTaskExecutor.class);
        ImportHandlerCallablesFactory callablesFactory = mock(ImportHandlerCallablesFactory.class);
        InternalRepositoryService repositoryService = mock(InternalRepositoryService.class);

        ImportJob importJob =
                new ImportJob(asyncTaskExecutor, callablesFactory, new Semaphore(3), repositoryService,
                        mock(StorageService.class), mock(ReleaseBundleAddon.class));

        List<String> repositoryKeys = new ArrayList<>(List.of("repo1", "repo2", "repo3"));
        File importRootFile = null;
        try {
            importRootFile = createDirectories(repositoryKeys);
            importRootFile.mkdir();

            ImportSettings importSettings = new RepositoryImportSettingsImpl(importRootFile);
            importSettings.setRepositories(repositoryKeys);

            DbRepoImportHandler repo1Handler = mock(DbRepoImportHandler.class);
            Callable<DbRepoImportHandler> repo1Callable = () -> repo1Handler;

            DbRepoImportHandler repo2Handler = mock(DbRepoImportHandler.class);
            Callable<DbRepoImportHandler> repo2Callable = () -> repo2Handler;

            DbRepoImportHandler repo3Handler = mock(DbRepoImportHandler.class);
            Callable<DbRepoImportHandler> repo3Callable = () -> repo3Handler;

            Map<String, ImportSettings> internalDataMap = new HashMap<>();
            internalDataMap.put(RepositoryImportSettingsImpl.class.getName(), importSettings);
            JobDataMap jobDataMap = new JobDataMap(internalDataMap);

            JobDetailImpl jobDetail = new JobDetailImpl();
            jobDetail.setJobDataMap(jobDataMap);

            JobExecutionContext jobExecutionContext = mock(JobExecutionContext.class);
            when(jobExecutionContext.getJobDetail()).thenReturn(jobDetail);
            when(repositoryService.getVirtualRepositories()).thenReturn(Collections.emptyList());
            when(callablesFactory.create(eq("repo1"), any(File.class))).thenReturn(repo1Callable);
            when(callablesFactory.create(eq("repo2"), any(File.class))).thenReturn(repo2Callable);
            when(callablesFactory.create(eq("repo3"), any(File.class))).thenReturn(repo3Callable);

            Future<DbRepoImportHandler> future1 = mock(Future.class);
            Future<DbRepoImportHandler> future2 = mock(Future.class);
            Future<DbRepoImportHandler> future3 = mock(Future.class);

            try {
                when(future1.get()).thenReturn(repo1Handler);
                when(future2.get()).thenReturn(repo2Handler);
                when(future3.get()).thenReturn(repo3Handler);

            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }

            when(asyncTaskExecutor.submit(repo1Callable)).thenReturn(future1);
            when(asyncTaskExecutor.submit(repo2Callable)).thenReturn(future2);
            when(asyncTaskExecutor.submit(repo3Callable)).thenReturn(future3);

            importJob.onExecute(jobExecutionContext);
            verify(repositoryService, times(1)).getVirtualRepositories();
            verify(callablesFactory, times(1)).create(eq("repo1"), any(File.class));
            verify(callablesFactory, times(1)).create(eq("repo2"), any(File.class));
            verify(callablesFactory, times(1)).create(eq("repo3"), any(File.class));


            verify(asyncTaskExecutor, times(1)).submit(repo1Callable);
            verify(asyncTaskExecutor, times(1)).submit(repo2Callable);
            verify(asyncTaskExecutor, times(1)).submit(repo3Callable);

            verify(repo1Handler, times(1)).finalizeImport();
            verify(repo2Handler, times(1)).finalizeImport();
            verify(repo3Handler, times(1)).finalizeImport();
        } finally {
            importRootFile.delete();
        }
    }

    private File createDirectories(Collection<String> dirNames) {
        File root = Files.createTempDir();
        root.setExecutable(true);
        root.setWritable(true);
        for (String dirName : dirNames
        ) {
            new File(root, dirName).mkdir();
        }
        return root;
    }
}
