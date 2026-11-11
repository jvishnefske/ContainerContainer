package org.artifactory.ui.rest.model.admin.security.signingkey;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.artifactory.rest.common.model.BaseModel;

import java.io.Serializable;

/**
 * @author Michael Akushsky
 */
@Getter
@Setter
@NoArgsConstructor
public class VaultPath extends BaseModel implements Serializable {

    private String path;
    private String key;
}
