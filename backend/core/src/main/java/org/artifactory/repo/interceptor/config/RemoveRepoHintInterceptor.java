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

package org.artifactory.repo.interceptor.config;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.events.EventLogHintManager;
import org.artifactory.opentracing.ArtifactoryTracer;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.interceptor.RepoConfigChangeInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.artifactory.event.EventHintType.RemoveRepo;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_DELETE;

/**
 * @author Fred Simon
 */
@Component
public class RemoveRepoHintInterceptor implements RepoConfigChangeInterceptor {
    private final ArtifactoryTracer tracer;

    @Autowired
    public RemoveRepoHintInterceptor(ArtifactoryTracer tracer) {
        this.tracer = tracer;
    }

    /**
     * On remove repo Add RemoveRepo hint on events
     */
    @Override
    public void onBeforeSave(Map<RepoDetailsType, List<RepoConfigChange>> mutableRepoConfigChanges) {
        //Verify repo was was removed
        boolean repoWasRemoved = mutableRepoConfigChanges.values().stream()
                .flatMap(Collection::stream)
                .anyMatch(repoConfigChange -> REPO_DELETE.equals(repoConfigChange.getConfigAction()));
        if (repoWasRemoved) {
            //Mark remove repo events with RemoveRepo
            EventLogHintManager.put(RemoveRepo, traceId());
        }
    }

    private String traceId() {
        String traceId = null;
        if (tracer != null) {
            try {
                traceId = tracer.getCurrentSpanTraceId();
            } catch (Exception e) {
                //Could occur in tests
                //Exception is harmless, the trace id is for debugging only for now.
            }
        }
        if (StringUtils.isBlank(traceId)) {
            traceId = "unknown";
        }
        return traceId;
    }
}
