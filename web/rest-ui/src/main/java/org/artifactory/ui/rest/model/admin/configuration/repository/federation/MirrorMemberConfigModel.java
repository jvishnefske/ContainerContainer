package org.artifactory.ui.rest.model.admin.configuration.repository.federation;

import lombok.Data;
import org.artifactory.rest.common.model.RestModel;

/**
 * @author AndreiK.
 */
@Data
public class MirrorMemberConfigModel implements RestModel {
    String url;
    String mode;
    boolean enabled = true;
}
