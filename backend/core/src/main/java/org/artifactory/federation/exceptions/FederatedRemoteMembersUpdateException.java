package org.artifactory.federation.exceptions;

import java.util.List;

public class FederatedRemoteMembersUpdateException extends RuntimeException{
    private List<String> failures;

    public FederatedRemoteMembersUpdateException(List<String> failures, String message) {
        super(message);
        this.failures = failures;
    }

    public List<String> getUrls() {
        return failures;
    }

    public void setUrls(List<String> failures) {
        this.failures = failures;
    }
}
