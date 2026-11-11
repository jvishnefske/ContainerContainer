package org.artifactory.repo.local;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.artifactory.common.config.metadata.MetadataEnum;
import org.jfrog.common.config.diff.GenerateDiffFunction;

import jakarta.xml.bind.annotation.XmlEnumValue;

/**
 * Checksum policy of local non-cache repositories, determines which value to return when client requests checksum.
 *
 * @author Yossi Shaul
 */
@GenerateDiffFunction(typeMethod = "getMessage")
public enum LocalRepoChecksumPolicyType implements MetadataEnum {
    @XmlEnumValue("client-checksums") @JsonProperty("CLIENT") CLIENT("client-checksums"),
    @XmlEnumValue("server-generated-checksums") @JsonProperty("SERVER") SERVER("server-generated-checksums");

    String message;

    LocalRepoChecksumPolicyType(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    /**
     * return LocalRepoChecksumPolicyType  if found from message, ignore case otherwise return null.
     * @param message
     * @return LocalRepoChecksumPolicyType
     */
    public static LocalRepoChecksumPolicyType fromString(String message) {
        for (LocalRepoChecksumPolicyType localRepoChecksumPolicyType : LocalRepoChecksumPolicyType.values()) {
            if (localRepoChecksumPolicyType.message.equalsIgnoreCase(message)) {
                return localRepoChecksumPolicyType;
            }
        }
        return null;
    }

    @Override
    public String getConfigName() {
        return message;
    }
}
