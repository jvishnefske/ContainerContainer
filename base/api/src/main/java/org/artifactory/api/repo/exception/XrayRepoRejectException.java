package org.artifactory.api.repo.exception;


/**
 * @author William Arustamyan
 */
public final class XrayRepoRejectException extends RepoRejectException {

    public XrayRepoRejectException(String message, int status) {
        super(message, status);
    }
}
