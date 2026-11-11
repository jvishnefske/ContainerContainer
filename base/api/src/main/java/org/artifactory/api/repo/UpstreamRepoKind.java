package org.artifactory.api.repo;

/**
 * @author igoru
 */
public enum UpstreamRepoKind {
    SMART(""),
    COMMON("Only smart remote repositories are supported with Artifactory Edge license"),
    UNKNOWN("Upstream repository not found or not accessible"),
    UNAUTHORIZED("Upstream repository credentials or user permissions insufficient"),
    NOT_CONFIGURED("Upstream repository is not configured properly"),
    NOT_ARTIFACTORY("Upstream instance is not Artifactory with Edge license"),
    NOT_AVAILABLE("Upstream instance is currently not responding");

    String validationMessage;

    UpstreamRepoKind(String validationMessage) {
        this.validationMessage = validationMessage;
    }

    public String getValidationMessage() {
        return validationMessage;
    }

    public boolean notSmart() {
        return !this.equals(UpstreamRepoKind.SMART);
    }
}