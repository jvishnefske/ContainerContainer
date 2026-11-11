package org.artifactory.aol;

import org.apache.http.auth.AuthenticationException;
import org.artifactory.api.repo.DockerRepositoryAction;
import org.artifactory.security.UserInfo;

import java.io.IOException;

/**
 * @author Alexei Vainshtein
 */
public interface AolService {
    String SUPER_USER_NAME = "super";

    /**
     * @return true if the authenticated user is the dashboard user
     */
    boolean isDashboardUser();

    /**
     * Check if a certain user is the AOL administrator, by checking that via the AOL dashboard.
     *
     * @param userInfo The user to check if its deletion is allowed.
     * @return True if the user is the AOL admin user
     */
    boolean isAolAdmin(UserInfo userInfo);

    /**
     * @return True if running AOL
     */
    boolean isAol();

    /**
     * @return the purchase url from the store
     */
    default String getPurchaseUrl() throws IOException, AuthenticationException {
        return null;
    }

    /**
     * Sends to the store regarding docker repo created or deleted events to register the dns
     * @param repoName - docker repo name
     * @param dockerApiVersion - The docker api version
     * @param action - The action if created or deleted
     */
    default void sendDockerRepoEvent(String repoName, String dockerApiVersion, DockerRepositoryAction action) {

    }

    /**
     * @return true if the flag configured of skipping the docker dns
     */
    default boolean skipDockerDns() {
        return false;
    }
}
