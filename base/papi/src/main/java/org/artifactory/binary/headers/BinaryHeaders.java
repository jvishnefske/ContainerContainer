package org.artifactory.binary.headers;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.artifactory.checksum.ChecksumsInfo;

/**
 * Binary checksum and content length to populate the metadata part of org.jfrog.storage.binstore.ifc.BinaryStream
 * This will be set if they are known at that stage.
 * requests from clients that does not include content length on upload will not populate content length part.
 *
 * @author gidis
 */
@Getter
@AllArgsConstructor
public class BinaryHeaders {

    private ChecksumsInfo checksumsInfo;
    private String contentLength;
    private String repoKey;
}
