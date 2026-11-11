package org.artifactory.config;

import org.jfrog.common.config.diff.DataDiff;

import java.util.List;

/**
 * Central config diff validator is taking care of config descriptor changes authorities
 *
 * @author BarakH
 */
public interface CentralConfigDiffValidation {

    /**
     * @param dataDiffs - Diff between old and new config descriptors
     */
    void validate(List<DataDiff<?>> dataDiffs) throws IllegalArgumentException;
}
