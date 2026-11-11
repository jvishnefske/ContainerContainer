package org.artifactory.repo.reponse;

import lombok.Builder;
import lombok.Getter;

/**
 * @author Tamar Rosen
 */
@Builder
@Getter
public class RecoveryRtfsResponse {
    String message;
}
