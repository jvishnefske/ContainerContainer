package org.artifactory.rest.common.model.xray;


import lombok.Data;
import lombok.EqualsAndHashCode;
import org.artifactory.rest.common.model.BaseModel;

import java.util.List;

/**
 * Author: Ivan Perepelytsya
 */
@Data
@EqualsAndHashCode(callSuper=false)
public class RepositoriesHolder extends BaseModel {
    private List<String> repos;
}