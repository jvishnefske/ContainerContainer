package org.artifactory.addon.sso.saml;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author BarakH
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SamlLoginId {
    private String loginId;
}
