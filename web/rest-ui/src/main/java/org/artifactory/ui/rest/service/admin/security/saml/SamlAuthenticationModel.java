package org.artifactory.ui.rest.service.admin.security.saml;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Nadav Yogev
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
class SamlAuthenticationModel {
    private Boolean isSamlAuthentication;
    private String samlSessionIndex;
}
