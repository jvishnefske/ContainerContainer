package org.artifactory.repo.federated;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import org.artifactory.common.config.metadata.MetadataInfo;
import org.artifactory.repo.FederatedMemberConfiguration;
import org.jfrog.common.config.diff.DiffKey;
import org.jfrog.common.config.diff.GenerateDiffFunction;

/**
 * @author Tamar Rosen
 */
@Data
@GenerateDiffFunction
public class FederatedMember {
    public static final String BASE_URL_PLACEHOLDER = "<base-url-placeholder>";
    @DiffKey
    @MetadataInfo
    @Getter(value = AccessLevel.NONE)
    private String url;
    @MetadataInfo
    private String mode;
    @MetadataInfo(defaultValue = "true")
    private boolean enabled = true;

    public FederatedMember() {
    }

    public FederatedMember(FederatedMember federatedMember) {
        this.url = federatedMember.getUrlOrTemplate();
        this.mode = federatedMember.getMode();
        this.enabled = federatedMember.isEnabled();
    }

    /**
     * Returns the federated member URL that may or may not contain a baseURL placeholder (template), depending on
     * the type of federated member (remote, local)
     * The placeholder URL that is returned from this method will be resolved dynamically, rather than containing a
     * strict base URL string.
     *
     * @return the full federated member URL for remote members, and a URL template for the local member.
     */
    @JsonProperty("url")
    public String getUrlOrTemplate() {
        return url;
    }

    @JsonSetter("url")
    public void setUrlOrTemplate(String url) {
        this.url = url;
    }

    /**
     * Returns the member URL (or URL template for the local member).
     * <p>
     * This method exists solely to satisfy the {@link org.jfrog.common.config.diff.DiffKey} contract:
     * the diff engine resolves list-element keys via a standard {@code get<FieldName>()} getter,
     * so {@code getUrl()} must be present for the {@code url} field annotated with {@code @DiffKey}.
     * For all other purposes prefer {@link #getUrlOrTemplate()}, which is the canonical accessor.
     */
    @JsonIgnore
    public String getUrl() {
        return getUrlOrTemplate();
    }

    /**
     * Converts this FederatedMember to a descriptor FederatedMember.
     *
     * @return a new descriptor FederatedMember with the same properties
     */
    public org.artifactory.descriptor.repo.FederatedMember toDescriptor() {
        org.artifactory.descriptor.repo.FederatedMember descriptorMember =
                new org.artifactory.descriptor.repo.FederatedMember();
        descriptorMember.setUrl(this.getUrlOrTemplate());
        descriptorMember.setEnabled(this.isEnabled());
        descriptorMember.setMode(this.getMode());
        return descriptorMember;
    }

    /**
     * Creates a FederatedMember from a FederatedMemberConfiguration.
     *
     * @param config the configuration to convert
     * @return a new FederatedMember with properties from the configuration
     */
    public static FederatedMember from(FederatedMemberConfiguration config) {
        FederatedMember member = new FederatedMember();
        member.setUrlOrTemplate(config.getUrl());
        member.setMode(config.getMode());
        Boolean enabled = config.getEnabled();
        member.setEnabled(enabled == null || enabled);
        return member;
    }
}
