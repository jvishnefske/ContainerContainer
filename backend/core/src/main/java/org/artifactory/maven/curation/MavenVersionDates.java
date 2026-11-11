package org.artifactory.maven.curation;

import java.time.Instant;

/**
 * @author Ron Malka
 */

public record MavenVersionDates(String version, Instant releaseDate) {
}
