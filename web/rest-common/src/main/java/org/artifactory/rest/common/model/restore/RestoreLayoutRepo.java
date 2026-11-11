package org.artifactory.rest.common.model.restore;

import org.artifactory.common.repo.PackageType;

/**
 * @author shahafg
 * */
public record RestoreLayoutRepo(String repoKey, PackageType packageType) {
}