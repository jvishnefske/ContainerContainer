package org.artifactory.addon.curation.packages;

import com.jfrog.ph.acl.exception.PackageBlockedByCurationException;
import com.jfrog.ph.acl.exception.PackageCurationException;
import com.jfrog.ph.acl.model.PackageDownloadContext;
import com.jfrog.ph.acl.model.curation.CurationRequest;
import com.jfrog.ph.acl.model.curation.CurationResponse;
import org.artifactory.ph.curation.PackagesCurationMetadataWrapper;

import java.io.InputStream;

/**
 * Interface for controlling the curation process of package version metadata.
 * <p>
 * Implementations handle the creation of curation requests, processing of curation responses,
 * and conversion between metadata models and streams. Also provides utility methods for
 * determining if curation should be applied and for generating curated file paths.
 *
 * @param <T> The type representing the package metadata model.
 * @author Ron Malka
 */

public interface PackageVersionCurationController<T extends PackagesCurationMetadataWrapper> {

    String JFROG_CURATED_FOLDER = ".jfrog/curated/";

    /**
     * Creates a curation request for metadata curation.
     *
     * @param originMetadataWrapper The original metadata model to be curated.
     * @param repoKey               The repository key where the metadata is stored.
     * @param remoteUrl             The remote URL of the package.
     * @return A CurationRequest object containing the necessary data for curation.
     * @throws PackageCurationException If an error occurs while creating the curation request.
     */
    CurationRequest createMetadataCurationRequest(T originMetadataWrapper, String repoKey,
            String remoteUrl) throws PackageCurationException;

    /**
     * Processes the curation response and returns the curated metadata model.
     *
     * @param curationResponse      The response from the curation process.
     * @param originMetadataWrapper The original metadata model.
     * @return The curated metadata model.
     * @throws PackageCurationException If an error occurs during processing.
     */
    T getCuratedMetadataModel(CurationResponse curationResponse, T originMetadataWrapper)
            throws PackageCurationException;

    /**
     * Converts an input stream to the original metadata model.
     *
     * @param originMetadataStream The input stream containing the original metadata.
     * @return The original metadata model.
     * @throws PackageCurationException If an error occurs during conversion.
     */
    T getMetadataModelOrigin(InputStream originMetadataStream) throws PackageCurationException;

    /**
     * Converts the metadata model to an input stream.
     *
     * @param metadataWrapper The metadata model to convert.
     * @return An input stream representing the metadata.
     */
    InputStream toInputStream(T metadataWrapper) throws PackageCurationException;

    /**
     * Determines if curation should be checked for the given path.
     *
     * @param path The path to check.
     * @return True if curation should be checked, false otherwise.
     */
    boolean shouldCheckForCuration(String path);

    /**
     * Returns the curated file path for the given download context.
     *
     * @param downloadContext The context of the package download.
     * @return The curated file path.
     */
    default String getCuratedFilePath(PackageDownloadContext downloadContext) {
        String path = downloadContext.getPath();
        return JFROG_CURATED_FOLDER + path;
    }

    /**
     * Throws when curation blocks package metadata (e.g. forbidden marker cache hit or package-level block).
     * Package implementations may override to attach response headers; default throws without extra headers.
     */
    default void throwPackageBlockedByCurationException(String message) throws PackageBlockedByCurationException {
        throw new PackageBlockedByCurationException(message);
    }
}
