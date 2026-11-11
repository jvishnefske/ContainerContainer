package org.artifactory.ui.rest.model.setmeup;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.artifactory.rest.common.model.BaseModel;

import java.util.List;

/**
 * Request model for getting multiple repositories by their keys.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GetReposByKeysRequest extends BaseModel {
    private List<String> repoKeys;
}