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

package org.artifactory.ui.rest.service.admin.advanced.systemlogs;

import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.rest.common.service.StreamRestResponse;
import org.artifactory.ui.rest.model.admin.advanced.systemlogs.SystemLogFile;
import org.artifactory.ui.rest.model.admin.advanced.systemlogs.SystemLogsInitialize;
import org.artifactory.util.AolUtils;
import org.jfrog.common.system.logs.SystemLogFileModel;
import org.jfrog.common.util.SystemLogsUtil;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * @author Lior Hasson
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class GetSysLogDownloadLinkService implements RestService {

    private File logDir = ContextHelper.get().getArtifactoryHome().getLogDir();

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        updateResponseWithLogFile(request, response);
    }

    /**
     * update response with the log data file
     *
     * @param artifactoryRequest  - encapsulate data related to the request
     * @param artifactoryResponse - encapsulate data related to the response
     */
    private void updateResponseWithLogFile(@NonNull ArtifactoryRestRequest artifactoryRequest,
            @NonNull RestResponse artifactoryResponse) {
        String selectedLog = artifactoryRequest.getQueryParamByKey("id");
        SystemLogsInitialize systemLogsInitialize = new SystemLogsInitialize();
        if (!systemLogsInitialize.getLogs().contains(selectedLog) && AolUtils.isAol()) {
            artifactoryResponse.responseCode(HttpStatus.SC_NOT_FOUND).error("Log named " + selectedLog + " not found!");
            return;
        }
        SystemLogFileModel systemLogFileModel = SystemLogsUtil.getSystemLogFileModel(selectedLog, logDir);
        ((StreamRestResponse) artifactoryResponse).setDownloadFile(systemLogFileModel.getFilename());
        ((StreamRestResponse) artifactoryResponse).setDownload(true);
        if (!StringUtils.isBlank(systemLogFileModel.getErrorMessage())) {
            artifactoryResponse.error(systemLogFileModel.getErrorMessage());
            return;
        }
        artifactoryResponse.iModel(getSystemLogFile(systemLogFileModel));
    }

    @NonNull
    private SystemLogFile getSystemLogFile(SystemLogFileModel systemLogFileModel) {
        SystemLogFile logFileModel = new SystemLogFile();
        logFileModel.setStream(systemLogFileModel.getStream());
        return logFileModel;
    }

}
