package org.artifactory.ui.rest.service.admin.configuration.repositories;

/**
 * @author Oleksandr Legenkiy
 */
public record RepoKeyLengthLimits(int hardLimit, Integer softLimit) {
}
