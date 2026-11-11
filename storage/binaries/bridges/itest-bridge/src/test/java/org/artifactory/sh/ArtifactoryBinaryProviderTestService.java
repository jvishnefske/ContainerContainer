package org.artifactory.sh;

import org.jfrog.storage.binstore.manager.BinaryProviderManager;

public interface ArtifactoryBinaryProviderTestService {
    BinaryProviderManager getBinaryProviderManager();
}
