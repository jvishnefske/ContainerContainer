package org.artifactory.ui.rest.model.continuous.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author Hussein Alshqerat
 */
@AllArgsConstructor
@Data
@EqualsAndHashCode(callSuper=false)
public class ContinueArtifactsDto extends ContinuePageDto {
    String repoKey;
    String path;
    String repoType;
    String repoPkgType;
    String text;
    boolean isArchive;
    boolean isFolder;
}