package org.artifactory.ui.rest.model.onboarding;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.artifactory.rest.common.model.BaseModel;

import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = false)
public class SupportedRequestModel extends BaseModel {
    private Set<String> repoKeys;
}
