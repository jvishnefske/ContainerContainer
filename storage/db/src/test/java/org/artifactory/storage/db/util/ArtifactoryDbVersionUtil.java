package org.artifactory.storage.db.util;

import io.vavr.collection.Stream;
import lombok.experimental.UtilityClass;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.DbVersion;
import org.jfrog.converter.model.ConverterExecutionType;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Volodymyr Shulga
 */
@UtilityClass
public class ArtifactoryDbVersionUtil {

    /**
     * Returns version that are between passed in params.
     *
     * @param from - version is excluded from the list.
     * @param to   - this version is included in returned list.
     * @return list of versions between
     */
    public static List<DbVersion> versionsBetween(ArtifactoryDBVersion from, ArtifactoryDBVersion to) {
        return Stream.of(ArtifactoryDBVersion.get(ConverterExecutionType.SEQ_SYNC))
                .filter(dbVersion -> isBetween(dbVersion.version(), from, to))
                .sorted(Comparator.comparingInt(dbVersion -> Integer.parseInt(dbVersion.version().substring(1))))
                .collect(Collectors.toList());
    }

    public boolean isBetween(String testing, ArtifactoryDBVersion from, ArtifactoryDBVersion to) {
        return isGreaterOrEqual(testing, from) && isLessOrEqual(testing, to);
    }

    public boolean isLessOrEqual(String testing, ArtifactoryDBVersion to) {
        int testingInt = Integer.parseInt(testing.substring(1));
        int toInt = Integer.parseInt(to.version().substring(1));
        return testingInt <= toInt;
    }

    public boolean isGreaterOrEqual(String testing, ArtifactoryDBVersion from) {
        int testingInt = Integer.parseInt(testing.substring(1));
        int fromInt = Integer.parseInt(from.version().substring(1));
        return testingInt >= fromInt;
    }
}
