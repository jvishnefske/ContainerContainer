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
import org.artifactory.livelogs.LiveLogsService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.exception.http.ForbiddenException;
import org.artifactory.ui.rest.model.admin.advanced.systemlogs.SystemLogData;
import org.artifactory.ui.rest.model.admin.advanced.systemlogs.SystemLogsInitialize;
import org.artifactory.util.AolUtils;
import org.jfrog.common.system.logs.SystemLogDataModel;
import org.jfrog.common.util.LiveLogsParams;
import org.jfrog.common.util.SystemLogsUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * @author Hezi Cohen
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class GetSysLiveLogsDataService implements RestService {

    private final File logDir = ContextHelper.get().getArtifactoryHome().getLogDir();
    private final LiveLogsService liveLogsService;

    @Autowired
    public GetSysLiveLogsDataService(LiveLogsService liveLogsService) {
        this.liveLogsService = liveLogsService;
    }

    @Override
    public void execute(@NonNull ArtifactoryRestRequest request, @NonNull RestResponse response) {
        String selectedLog = request.getQueryParamByKey("id");
        liveLogsService.assertUserCanRefreshLog(selectedLog);
        SystemLogsInitialize systemLogsInitialize = new SystemLogsInitialize();
        if (!systemLogsInitialize.getLogs().contains(selectedLog) && AolUtils.isAol()) {
            response.responseCode(HttpStatus.SC_NOT_FOUND).error("Log named " + selectedLog + " not found!");
            return;
        }
        long lastPointer = 0L;
        if (StringUtils.isNotBlank(request.getQueryParamByKey("fileSize"))) { //backwards compatibility
            lastPointer = Long.parseLong(request.getQueryParamByKey("fileSize"));
        }
        if (StringUtils.isNotBlank(request.getQueryParamByKey("file_size"))) {
            lastPointer = Long.parseLong(request.getQueryParamByKey("file_size"));
        }

        if (request.getUriInfo().getPath().contains("system/logs")) {
            if (AolUtils.isAol()) {
                throw new ForbiddenException("Live Logs feature is not available for Artifactory SaaS.");
            }
            response.iModel(liveLogsService.getLiveLogsData(selectedLog, lastPointer));
        } else { //backwards compatibility
            File systemLogFile = SystemLogsUtil.getSelectedLogIfFound(selectedLog, logDir);
            if (systemLogFile == null) {
                response.responseCode(HttpStatus.SC_NOT_FOUND).error("Log named " + selectedLog + " not found!");
                return;
            }
            LiveLogsParams liveLogsParams = new LiveLogsParams(lastPointer, systemLogFile,
                    liveLogsService.getReadBlockSizeFromSystemYaml(), liveLogsService.getAdditionalSensitiveRegex());
            response.iModel(getSystemLogData(SystemLogsUtil.readLogFile(liveLogsParams)));
        }
    }

    @NonNull
    private SystemLogData getSystemLogData(@NonNull SystemLogDataModel systemLogDataModel) {
        SystemLogData sysLogData = new SystemLogData();
        sysLogData.setFileSize(systemLogDataModel.getFileSize());
        sysLogData.setLastUpdateLabel(systemLogDataModel.getLastUpdateLabel());
        sysLogData.setLastUpdateModified(systemLogDataModel.getLastUpdateModified());
        sysLogData.setLogContent(systemLogDataModel.getLogContent());
        return sysLogData;
    }
}
