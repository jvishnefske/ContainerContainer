package org.artifactory.config;

import org.apache.commons.lang3.builder.DiffResult;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.config.CentralConfigDescriptorImpl;
import org.artifactory.descriptor.config.DiffFunctionsImpl;
import org.jfrog.common.config.diff.DiffFunctions;

import jakarta.annotation.Nonnull;

/**
 * Diff between two config descriptors
 *
 * @author Noam Shemesh
 */
public class ArtifactoryConfigDiffUtil {

    private ArtifactoryConfigDiffUtil() {
        // util class
    }

    private static final DiffFunctions diffFunctions = new DiffFunctionsImpl();

    public static DiffResult findDiff(@Nonnull CentralConfigDescriptor oldDescriptor,
            @Nonnull CentralConfigDescriptor newDescriptor) {
        if (!(oldDescriptor instanceof CentralConfigDescriptorImpl) ||
                !(newDescriptor instanceof CentralConfigDescriptorImpl)) {
            throw new IllegalArgumentException("Specific implementation for CentralConfigDescriptor are not allowed");
        }
        return diffFunctions.diffFor(CentralConfigDescriptorImpl.class,
                (CentralConfigDescriptorImpl) oldDescriptor, (CentralConfigDescriptorImpl) newDescriptor);
    }
}
