package org.artifactory.rest.common.model.migrate;

import org.artifactory.common.repo.PackageType;

/**
 * @author Volodymyr Shulga
 */
public record MigrateRepo(String repoKey, PackageType packageType) {
}
