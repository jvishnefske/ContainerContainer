/*
 *
 * Copyright 2018 JFrog Ltd. All rights reserved.
 * JFROG PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package org.artifactory.repo.service.trash;

import org.artifactory.model.LocalConfig;
import org.artifactory.repo.cache.expirable.InternalMetadataChecker;
import org.springframework.stereotype.Component;

/**
 * @author Michael Akushsky
 */
@Component
public class InternalMetadataNonTrashable implements NonTrashableItem {

    @Override
    public boolean skipTrash(LocalConfig config, String path) {
        return InternalMetadataChecker.isInternalMetadata(path);
    }
}
