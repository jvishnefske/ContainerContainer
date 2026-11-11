package org.artifactory.descriptor.repo;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.artifactory.descriptor.Descriptor;
import org.jfrog.common.config.diff.GenerateDiffFunction;

import java.util.Objects;

/**
 * @author Shashank Taliwal
 */
@XmlType(name = "rpmType",
        propOrder = {"enableRecommendTags", "enableSha256"},
        namespace = Descriptor.NS)
@GenerateDiffFunction
@Data
public class RpmGeneralConfig implements Descriptor {

    @XmlElement(name = "enableRecommendTags", required = false)
    @Getter
    @Setter
    private RpmEnableRecommendTagsConfig enableRecommendTags = new RpmEnableRecommendTagsConfig();

    @XmlElement(name = "enableSha256", required = false)
    @Getter
    @Setter
    private RpmEnableSha256Config enableSha256 = new RpmEnableSha256Config();

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof RpmGeneralConfig that)) {
            return false;
        }
        return (Objects.equals(enableRecommendTags, that.enableRecommendTags)) &&
                (Objects.equals(enableSha256, that.enableSha256));
    }

    @Override
    public int hashCode() {
        return Objects.hash(enableRecommendTags, enableSha256);
    }

}
