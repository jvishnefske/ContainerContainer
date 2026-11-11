package org.artifactory.api.rest.release;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Hezi Cohen
 */

@Data
@NoArgsConstructor
@AllArgsConstructor(onConstructor_={@JsonIgnore})
public class ReleaseBundleArtifactsModel {

    private List<String> repoPaths = new ArrayList<>();

}
