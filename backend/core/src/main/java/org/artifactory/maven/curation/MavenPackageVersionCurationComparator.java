package org.artifactory.maven.curation;

import org.artifactory.maven.versioning.MavenVersionComparator;

import java.time.Instant;
import java.util.Comparator;

/**
 * Comparator for {@link MavenVersionDates} that orders versions by release date in descending order.
 * <p>
 * If the release dates are equal or either is {@link Instant#EPOCH}, the comparison falls back to
 * Maven version ordering using {@link MavenVersionComparator}.
 *
 * @author Ron Malka
 *
 */

public class MavenPackageVersionCurationComparator implements Comparator<MavenVersionDates> {
    @Override
    public int compare(MavenVersionDates o1, MavenVersionDates o2) {
        int res = o2.releaseDate().compareTo(o1.releaseDate());
        // If both release dates are equal or one of them is Instant.EPOCH, compare by version
        if (res == 0 || o1.releaseDate() == Instant.EPOCH || o2.releaseDate() == Instant.EPOCH) {
            res = new MavenVersionComparator().compare(o2.version(), o1.version());
        }
        return res;
    }


}
