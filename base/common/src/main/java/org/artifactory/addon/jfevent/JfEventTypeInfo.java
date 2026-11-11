package org.artifactory.addon.jfevent;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Yevdo Abramov
 * Created on 24/05/2020
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class JfEventTypeInfo {
    private String id;
    private String name;
    private String description;
    private String payloadSample;
    @JsonProperty("support_projects")
    private Boolean supportProjects;
    @JsonProperty("require_admin")
    private Boolean requireAdmin;

    public JfEventTypeInfo(EventTypeInfo typeInfo, String payloadSample,
            Boolean supportProjects, Boolean requireAdmin) {
        this.id = typeInfo.getCode();
        this.name = typeInfo.getName();
        this.description = typeInfo.getDescription();
        this.payloadSample = payloadSample;
        this.supportProjects = supportProjects;
        this.requireAdmin = requireAdmin;
    }
}