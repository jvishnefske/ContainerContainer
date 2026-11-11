/*
 *
 * Copyright 2018 JFrog Ltd. All rights reserved.
 * JFROG PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package org.artifactory.storage.db.initializer;

import org.artifactory.common.config.db.ArtifactoryDbProperties;

import java.util.Map;
import java.util.Properties;

/**
 * @author haims
 */
public interface TestDBInitializer {
    Properties setup(String dbSchemaName);

    void setDbName(String name);

    Properties getProps();

    void setForceAnotherDbForLocking(boolean forceAnotherDbForLocking);

    boolean isForceAnotherDbForLocking();

    /**
     * Sets optional database props to override the default values loaded from the file. <p/>
     * See {@link ArtifactoryDbProperties} for more details about the database props.
     *
     * @param customDbProps Key value map of database props.
     */
    void setCustomDbProps(Map<String, String> customDbProps);
}
