package org.artifactory.ui.rest.service.admin.advanced.systemlogs;

import lombok.NonNull;
import org.artifactory.livelogs.LiveLogsService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.exception.http.ForbiddenException;
import org.artifactory.util.AolUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class GetSysLiveLogsConfigService implements RestService {

    private final LiveLogsService liveLogsService;

    @Autowired
    public GetSysLiveLogsConfigService(LiveLogsService liveLogsService) {
        this.liveLogsService = liveLogsService;
    }

    @Override
    public void execute(@NonNull ArtifactoryRestRequest request, @NonNull RestResponse response) {
        if (AolUtils.isAol()) {
            throw new ForbiddenException("Live Logs feature is not available for Artifactory SaaS.");
        }
        response.iModel(liveLogsService.getLiveLogsConfig()).buildResponse();
    }
}
