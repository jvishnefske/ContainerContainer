package org.artifactory.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.model.RepoConfig;

import java.util.Set;

/**
 * This class represents a change to the central config descriptor
 *
 * @author Mor Merhav
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CentralConfigDescriptorChange {

    private MutableCentralConfigDescriptor oldCentralConfigDescriptor;
    private MutableCentralConfigDescriptor newCentralConfigDescriptor;
    private Set<RepoConfig> repoConfigsToUpdate;
    private boolean saveDescriptor;
    private boolean bootstrapInitOrUpgradeFlowFlag;
}
