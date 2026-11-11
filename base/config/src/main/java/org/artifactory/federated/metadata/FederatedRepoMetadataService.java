package org.artifactory.federated.metadata;

import org.artifactory.model.FederatedRepoConfig;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * The service for retrieving the metadata object json and invalidating the cache
 *
 * @author Alexei Vainshtein
 */
public interface FederatedRepoMetadataService {

    final String SUFFIX = "/artifactory";

    /**
     * Returns package metadata of a remote member.
     * First goes to cache, if not cached (or expired), will try secondly in the db.
     * If missed in the db (cached expired or not cached), will go and try from the remote mirror.
     *
     * @param packageType - The package type to get the cached metadata
     * @param url         - The member url
     * @param proxyRef    - The proxy name if configured. Can be null
     * @return the metadata json of the requested package type from the remote member.
     * @throws MetadataRetrievalException upon error fetching the metadata
     */
    String getPackageMetadata(String packageType, String url, String proxyRef) throws MetadataRetrievalException;

    /**
     * Returns package metadata of the running service.
     * This is calculated internally upon demand.
     *
     * @param packageType - The package type to get the metadata for
     * @param baseUrl     - The server base url
     * @return the metadata json of the requested package type from the remote member.
     */
    String getPackageMetadataForLocal(String packageType, String baseUrl);

    /**
     * @return a json of the basic configuration.
     * Represents object {@link org.artifactory.model.FederatedRepoConfig}
     */
    String getFederatedRepoConfigMetadata();


    /**
     * Invalidate the cache for the specific member
     *
     * @param packageType - The package type to be invalidated
     * @param baseUrl     - The member url
     * @param proxyRef    - The proxy name if configured. Can be null
     */
    default void invalidateCacheForMember(String packageType, String baseUrl, String proxyRef) {

    }

    /**
     * @return the highest metadata version combined from the basic and the package class's
     */
    default int fetchHighestMetadataVersion() {
        return Integer.MIN_VALUE;
    }

    /**
     * Extracts from the database the relevant metadata per package.
     * Grabs the information from the collection of repositories provided, collects all the required urls that needs
     * to be extracted from those repositories and fetch those from the db
     *
     * @param federatedRepositories - A collection of all the federated repositories to collect the metadata for
     * @return a Map of metadata per member for each repo
     */
    default Map<String /*repo*/, Map<String /*url*/, String /*metadata*/>> getPackagesMetadata(
            Collection<FederatedRepoConfig> federatedRepositories, boolean retry) {
        return Collections.emptyMap();
    }

    /**
     * Extracts from the database the relevant basic metadata.
     * Grabs the information from the collection of repositories provided, collects all the required urls that needs
     * to be extracted from those repositories and fetch those from the db
     *
     * @param federatedRepositories - A collection of all the federated repositories to collect the metadata for
     * @return a Map of metadata per member for each repo
     */
    default Map<String, Map<String, String>> getRemoteBasicMetadata(
            Collection<FederatedRepoConfig> federatedRepositories, boolean retry) {
        return Collections.emptyMap();
    }

    /**
     * Convert the values in the members column to have /artifactory on the missing members.
     */
    default void convertMembers(){}

    default String getSuffix() {
        return SUFFIX;
    }
}
