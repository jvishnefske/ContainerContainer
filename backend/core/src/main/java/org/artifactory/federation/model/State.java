package org.artifactory.federation.model;

import java.util.List;

/**
 * @author Tomer Slutsky
 */
public record State(String key, RepoStatus status, List<RemoteState> members) {
}
