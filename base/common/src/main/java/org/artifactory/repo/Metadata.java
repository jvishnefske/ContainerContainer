package org.artifactory.repo;

/**
 * Holds information regarding the metadata of pacakge type
 * and if negotiation required for this package type
 *
 * @author Alexei Vainshtein
 */
public record Metadata(String packageMetadata, boolean negotiationRequired) {
}
