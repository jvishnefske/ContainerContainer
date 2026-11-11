package org.artifactory.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.artifactory.common.config.metadata.MetadataInfo;
import org.artifactory.repo.xray.XrayNotAvailableMode;
import org.jfrog.common.config.diff.GenerateDiffFunction;
import org.jfrog.common.config.validation.NotUnicode;

import static org.artifactory.descriptor.repo.RepoBaseDescriptor.DEFAULT_SIGNED_URL_TTL;
import static org.artifactory.descriptor.repo.XrayRepoConfigDescriptor.DEFAULT_DATA_TTL;

/**
 * @author Tamar Rosen
 */
@Data
@NoArgsConstructor
@GenerateDiffFunction
public class SecurityConfig {
    @NotUnicode
    @MetadataInfo(skip = true)
    private String primaryKeyPairRef;
    @NotUnicode
    @MetadataInfo(skip = true)
    private String secondaryKeyPairRef;
    @MetadataInfo(skip = true)
    private XrayRepoConfig xrayConfig;
    @MetadataInfo(defaultValue = "false")
    private boolean hideUnauthorizedResources;
    @NotUnicode
    @MetadataInfo(skip = true)
    private String keyPair;
    @MetadataInfo(defaultValue = "90")
    private long signedUrlTtl = DEFAULT_SIGNED_URL_TTL;

    public SecurityConfig(SecurityConfig securityConfig) {
        this.primaryKeyPairRef = securityConfig.getPrimaryKeyPairRef();
        this.secondaryKeyPairRef = securityConfig.getSecondaryKeyPairRef();
        if (securityConfig.getXrayConfig() != null) {
            this.xrayConfig = new XrayRepoConfig();
            this.xrayConfig.setEnabled(securityConfig.getXrayConfig().isEnabled());
            this.xrayConfig.setDataTtl(securityConfig.getXrayConfig().getDataTtl());
            this.xrayConfig.setXrayNotAvailableMode(securityConfig.getXrayConfig().getXrayNotAvailableMode());
        }
        this.hideUnauthorizedResources = securityConfig.isHideUnauthorizedResources();
        this.keyPair = securityConfig.getKeyPair();
        this.signedUrlTtl = securityConfig.getSignedUrlTtl();
    }

    @JsonIgnore
    public boolean isXrayIndex() {
        return xrayConfig != null && xrayConfig.isEnabled();
    }

    public void setXrayIndex(boolean xrayIndex) {
        if (xrayConfig == null) {
            xrayConfig = new XrayRepoConfig();
        }
        xrayConfig.setEnabled(xrayIndex);
    }

    @JsonIgnore
    public int getXrayDataTtl() {
        if (xrayConfig != null) {
            return xrayConfig.getDataTtl();
        }
        return DEFAULT_DATA_TTL;
    }

    @JsonIgnore
    public XrayNotAvailableMode getXrayNotAvailableMode() {
        return xrayConfig != null ? xrayConfig.getXrayNotAvailableMode() : null;
    }

    public void setXrayNotAvailableMode(XrayNotAvailableMode xrayNotAvailableMode) {
        if (xrayConfig == null) {
            xrayConfig = new XrayRepoConfig();
        }
        xrayConfig.setXrayNotAvailableMode(xrayNotAvailableMode);
    }

    public void setXrayDataTtl(int xrayDataTtl) {
        if (xrayConfig == null) {
            xrayConfig = new XrayRepoConfig();
        }
        xrayConfig.setDataTtl(xrayDataTtl);
    }

    public void promoteKeyPair() {
        if (secondaryKeyPairRef != null) {
            primaryKeyPairRef = secondaryKeyPairRef;
            secondaryKeyPairRef = null;
        }
    }
}
