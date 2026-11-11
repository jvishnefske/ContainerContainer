package org.artifactory.resource;

import org.artifactory.checksum.ChecksumsInfo;

import java.util.Optional;

/**
 * @author Alexei Vainshtein
 */
public interface ResourceWithChecksumStreamHandle extends ResourceStreamHandle {

    /**
     *
     * @return the checksum info if presented. Can return empty optional if checksums is null.
     */
    Optional<ChecksumsInfo> getChecksumsInfo();

}
