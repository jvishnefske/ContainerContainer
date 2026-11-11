package org.artifactory.storage.service;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.sh.acl.ArtifactoryBinaryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author annakv
 */
@Service
@Slf4j
public class BinaryInfoServiceImpl implements BinaryInfoService {

    private final ArtifactoryBinaryService binaryService;

    @Autowired
    public BinaryInfoServiceImpl(ArtifactoryBinaryService binaryService) {
        this.binaryService = binaryService;
    }

    @Override
    public long getStorageSize() {
        log.debug("Getting storage size from the database");
        return binaryService.getStorageSize();
    }
}
