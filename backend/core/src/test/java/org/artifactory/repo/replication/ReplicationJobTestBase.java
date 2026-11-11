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

package org.artifactory.repo.replication;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.replication.ReplicationAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.mockito.Mockito;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.testng.annotations.BeforeMethod;

/**
 * @author Noam Y. Tenne
 */
public abstract class ReplicationJobTestBase extends ArtifactoryHomeBoundTest {

    protected final InternalArtifactoryContext artifactoryContext = Mockito.mock(InternalArtifactoryContext.class);
    protected final JobExecutionContext executionContext = Mockito.mock(JobExecutionContext.class);
    protected final JobDetail jobDetail = Mockito.mock(JobDetail.class);
    protected final JobDataMap jobDataMap = Mockito.mock(JobDataMap.class);
    protected final CentralConfigService configService = Mockito.mock(CentralConfigService.class);
    protected final SecurityService securityService = Mockito.mock(SecurityService.class);
    protected final AddonsManager addonsManager = Mockito.mock(AddonsManager.class);
    protected final ReplicationAddon replicationAddon = Mockito.mock(ReplicationAddon.class);
    protected final RepositoryService repositoryService = Mockito.mock(RepositoryService.class);

    @BeforeMethod
    public void reset() throws Exception {
        Mockito.reset(artifactoryContext, executionContext, jobDetail, jobDataMap, configService, securityService,
                addonsManager, replicationAddon, repositoryService);
    }
}
