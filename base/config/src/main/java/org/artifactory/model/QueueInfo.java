package org.artifactory.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Alexei Vainshtein
 */
@NoArgsConstructor(access = AccessLevel.PACKAGE)
@AllArgsConstructor
@Data
public class QueueInfo {

    private String remoteUrl;
    private String remoteRepoKey;
    private String repoKey;

}
