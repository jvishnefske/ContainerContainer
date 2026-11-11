package org.artifactory.ui.rest.service.admin.security.auth.authentication;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @author Nadav Yogev
 */
@Data
@NoArgsConstructor
public class AuthenticationStatusModel {

    public static final String ENABLED = "enabled";
    public static final String DISABLED = "disabled";
    public static final String NOT_FOUND = "not_found";
    public static final String UNAVAILABLE = "unavailable";

    private String status;
    private List<String> groups;
}
