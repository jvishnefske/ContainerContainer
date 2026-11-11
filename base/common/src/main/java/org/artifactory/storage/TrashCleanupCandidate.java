package org.artifactory.storage;

import lombok.Value;

/**
 * @author Oleksandr Legenkiy
 */
@Value
public class TrashCleanupCandidate {

    String sha1;
    String sha2;
    long length;

}
