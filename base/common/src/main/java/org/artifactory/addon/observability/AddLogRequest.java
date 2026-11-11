package org.artifactory.addon.observability;

import org.glassfish.jersey.media.multipart.FormDataMultiPart;

/**
 * @author Oleksandr Legenkiy
 */
public class AddLogRequest {

    private final RepoType repoType;
    private final String domain;
    private final String date;
    private final String logName;
    private final String userAgent;
    private final FormDataMultiPart formParams;

    public AddLogRequest(RepoType repoType, String domain, String date, String logName, String userAgent,
            FormDataMultiPart formParams) {
        this.repoType = repoType;
        this.domain = domain;
        this.date = date;
        this.logName = logName;
        this.userAgent = userAgent;
        this.formParams = formParams;
    }

    public RepoType getRepoType() {
        return repoType;
    }

    public String repoRelativePath() {
        return String.join("/", domain, date, logName);
    }

    public String getDomain() {
        return domain;
    }

    public String getDate() {
        return date;
    }

    public String getLogName() {
        return logName;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public FormDataMultiPart getFormParams() {
        return formParams;
    }

    public enum RepoType {
        USAGE,
        LOG_SHIPPING,
        BILLING
    }
}
