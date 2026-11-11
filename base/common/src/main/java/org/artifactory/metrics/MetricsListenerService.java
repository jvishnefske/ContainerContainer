package org.artifactory.metrics;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.metrics.callhome.CallHomeDataModel;
import org.artifactory.metrics.callhome.CallHomeMetrics;
import org.artifactory.util.HttpUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

@Service
@Slf4j
public class MetricsListenerService {
    private static final String SERVICE = ArtifactoryHome.ARTIFACTORY_SERVICE_NAME;
    private static final String UPLOAD_ACTION = "UPLOAD";
    private static final String DOWNLOAD_ACTION = "DOWNLOAD";
    private static final String NO_CONTENT = "_NO_CONTENT";
    RepositoryService repositoryService;
    CallHomeMetrics callHomeMetrics;

    @Autowired
    public MetricsListenerService(RepositoryService repositoryService, CallHomeMetrics callHomeMetrics) {
        this.repositoryService = repositoryService;
        this.callHomeMetrics = callHomeMetrics;
    }

    public void send(int statusCode, long responseContentLength, HttpServletRequest request) {
        List<MetricsInfo> repoInfoList = MetricsThreadLocal.getMetricsInfoList();
        if (null == repoInfoList) { return; }
        try {
            for (MetricsInfo repoInfo : repoInfoList) {
                String packageType = repoInfo.getRepoType();
                String action = repoInfo.getAction();
                String repoType = repoInfo.getRepoClass();
                String userAgent = request.getHeader(HttpHeaders.USER_AGENT);
                Long requestContentLength = HttpUtils.getContentLength(request);
                String remoteAddress = HttpUtils.getRemoteClientAddress(request);
                String statusFamily = StringUtils.isNotBlank(String.valueOf(statusCode)) ?
                        String.valueOf(statusCode).replaceFirst(".{2}$", "**") : "NA";
                if (StringUtils.isNotBlank(userAgent) && userAgent.startsWith("/")) {
                    userAgent = userAgent.substring(1);
                }

                action = transformActionName(action, requestContentLength, responseContentLength);

                CallHomeDataModel metricsObject = CallHomeDataModel.builder()
                        .service(SERVICE)
                        .action(action)
                        .statusCode(statusCode)
                        .statusFamily(statusFamily)
                        .tool(StringUtils.substringBefore(StringUtils.substringBefore(userAgent, "/"), " "))
                        .remoteAddress(remoteAddress)
                        .packageType(packageType)
                        .repoType(repoType)
                        .userAgent(userAgent)
                        .requestContentLength(requestContentLength)
                        .responseContentLength(responseContentLength)
                        .build();

                if(callHomeMetrics != null) {
                    callHomeMetrics.send(metricsObject);
                }
            }
        } catch (Exception e) {
            log.warn("Could not collect data for call home, {}", e.getMessage(), e);
        }
    }

    // Method to transform the action name based on upload/download conditions
    public String transformActionName(String action, Long requestContentLength, long responseContentLength) {
        boolean isNoContentUpload = UPLOAD_ACTION.equalsIgnoreCase(action) &&
                requestContentLength != null && requestContentLength.equals(-1L);
        boolean isNoContentDownload = DOWNLOAD_ACTION.equalsIgnoreCase(action) && responseContentLength == -1L;
        if(isNoContentUpload || isNoContentDownload) {
            action += NO_CONTENT;
        }
        return action;
    }
}
