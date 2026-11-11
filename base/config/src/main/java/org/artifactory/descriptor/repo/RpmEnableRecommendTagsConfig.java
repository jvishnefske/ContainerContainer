package org.artifactory.descriptor.repo;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.artifactory.descriptor.Descriptor;
import org.jfrog.common.config.diff.GenerateDiffFunction;

/**
 * @author Shashank Taliwal
 */
@XmlType(name = "rpmEnableRecommendsTagType",
        propOrder = {"visibleOnUi", "enabled"},
        namespace = Descriptor.NS)
@GenerateDiffFunction
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RpmEnableRecommendTagsConfig implements Descriptor {

    @XmlElement(name = "visibleOnUi", required = false)
    @Getter
    @Setter
    private boolean visibleOnUi = true;

    @XmlElement(name = "enabled", required = false)
    @Getter
    @Setter
    private boolean enabled = false;

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof RpmEnableRecommendTagsConfig other)) {
            return false;
        }
        return enabled == other.isEnabled() && visibleOnUi == other.isVisibleOnUi();
    }

    @Override
    public int hashCode() {
        int result = Boolean.hashCode(enabled);
        result = 31 * result + Boolean.hashCode(visibleOnUi);
        return result;
    }
}
