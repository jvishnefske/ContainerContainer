package org.artifactory.model;

import com.google.common.collect.Lists;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.artifactory.common.config.metadata.MetadataInfo;
import org.artifactory.repo.federated.FederatedMember;
import org.jfrog.common.config.diff.DiffIgnore;
import org.jfrog.common.config.diff.GenerateDiffFunction;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Federation configuration that can be embedded in BaseConfig.
 * This allows any repository type (Local/Remote/Virtual/Federated) to be federated.
 *
 * @author Daniel simantov
 */
@Data
@NoArgsConstructor
@GenerateDiffFunction
public class FederationConfig {

    /**
     * Grid topology ID for grid mode federation.
     * When set (with federationOnGrid=true), members are derived from the grid topology.
     */
    @MetadataInfo
    private String gridTopologyId;

    /**
     * Flag indicating whether federation uses grid mode.
     * true = grid mode (uses gridTopologyId)
     * false = off-grid mode (uses members list directly)
     */
    @MetadataInfo(defaultValue = "false")
    private boolean federationOnGrid;

    /**
     * List of federation members for off-grid mode.
     * Only used when federationOnGrid is false.
     */
    private List<FederatedMember> members = Lists.newArrayList();

    /**
     * Modification date for sync tracking.
     * Used to determine which configuration is newer during sync.
     */
    @MetadataInfo(defaultValue = "0")
    @DiffIgnore
    private long modificationDate;

    /**
     * Copy constructor for deep copy.
     *
     * @param other the FederationConfig to copy
     */
    public FederationConfig(FederationConfig other) {
        if (other != null) {
            this.gridTopologyId = other.gridTopologyId;
            this.federationOnGrid = other.federationOnGrid;
            this.modificationDate = other.modificationDate;
            if (other.members != null) {
                this.members = other.members.stream()
                        .map(FederatedMember::new)
                        .collect(Collectors.toCollection(ArrayList::new));
            }
        }
    }

    /**
     * True if at least one non-self member exists, or grid federation is enabled.
     * Self/local members are identified by {@code FederatedMember.BASE_URL_PLACEHOLDER} URL prefix.
     */
    public boolean isFederated() {
        if (federationOnGrid) {
            return true;
        }
        if (members == null || members.isEmpty()) {
            return false;
        }
        return members.stream().anyMatch(m -> {
            String url = m.getUrlOrTemplate();
            return url != null && !url.startsWith(FederatedMember.BASE_URL_PLACEHOLDER);
        });
    }

}

