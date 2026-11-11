package org.artifactory.addon.curation;

/**
 * Contains the payload used for Curation request and the status of the package.
 *
 * @author Igor Usenko
 */
public record CurationCachedPackageStatus(CurationPackageStatusPayload payload, CurationPackageStatus status) {
}
