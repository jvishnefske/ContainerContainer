package org.artifactory.aql.timeout;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.concurrent.TimeUnit;

@Getter
@AllArgsConstructor
public enum AqlQueryTimeout {
    FREE_TIER(TimeUnit.MINUTES.toSeconds(5L)),
    PRO_TIER(TimeUnit.MINUTES.toSeconds(15L)),
    ENT_TIER(TimeUnit.MINUTES.toSeconds(30L)),
    ENT_PLUS_TIER(TimeUnit.MINUTES.toSeconds(45L));

    private final Long timeout;
}
