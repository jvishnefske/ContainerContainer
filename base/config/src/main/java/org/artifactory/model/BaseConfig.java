package org.artifactory.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.artifactory.common.config.metadata.MetadataInfo;
import org.artifactory.common.config.metadata.MetadataVersion;
import org.jfrog.common.config.diff.DiffIgnore;
import org.jfrog.common.config.diff.GenerateDiffFunction;
import org.jfrog.common.config.validation.NotUnicode;

/**
 * This class contains base and common configurations which are relevant for any repository type
 * (Local/Remote/Virtual/Federated)
 *
 * @author Tamar Rosen
 */
@Data
@NoArgsConstructor
@MetadataVersion(metadataVersion = 1)
@GenerateDiffFunction
public class BaseConfig {
    @DiffIgnore
    @MetadataInfo(defaultValue = "0")
    private long modelVersion = 2;
    @NotUnicode
    @MetadataInfo
    private String description;
    @NotUnicode
    @MetadataInfo
    private String notes;
    @NotUnicode
    @MetadataInfo(skip = true)
    private String repoLayoutRef; // ex-xml-idref
    @NotUnicode
    @MetadataInfo(defaultValue = "**/*")
    private String includesPattern = "**/*";
    @NotUnicode
    @MetadataInfo
    private String excludesPattern;

    /**
     * Federation configuration for all repo types.
     * This allows Local/Remote/Virtual repos to be federated.
     * -- GETTER --
     *  Gets the federation config.
     *
     * @return the federation config (never null)

     */
    @MetadataInfo
    private FederationConfig federationConfig = new FederationConfig();

    BaseConfig(BaseConfig baseConfig) {
        this.modelVersion = baseConfig.modelVersion;
        this.description = baseConfig.getDescription();
        this.notes = baseConfig.getNotes();
        this.repoLayoutRef = baseConfig.getRepoLayoutRef();
        this.includesPattern = baseConfig.getIncludesPattern();
        this.excludesPattern = baseConfig.getExcludesPattern();
        this.federationConfig = new FederationConfig(baseConfig.federationConfig);
    }

    /**
     * Backward compatibility: Set gridTopologyId in federation config.
     * Handles JSON deserialization from old format where gridTopologyId was directly in BaseConfig.
     * Note: There is no corresponding getter - access via getFederationConfig().getGridTopologyId() instead.
     */
    @JsonSetter
    @JsonAlias("gridTopologyId")
    public void setGridTopologyId(String gridTopologyId) {
        getFederationConfig().setGridTopologyId(gridTopologyId);
    }

    /**
     * Checks if this repository is federated.
     *
     * @return true if federation is configured
     */
    @JsonIgnore
    public boolean isFederated() {
        return federationConfig != null && federationConfig.isFederated();
    }
}
