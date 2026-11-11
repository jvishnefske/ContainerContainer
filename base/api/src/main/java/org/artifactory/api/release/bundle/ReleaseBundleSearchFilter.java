package org.artifactory.api.release.bundle;

import lombok.Builder;
import lombok.Data;
import org.artifactory.bundle.BundleType;
import org.artifactory.common.ConstantValues;

import java.util.List;

/**
 * @author Lior Gur
 */
@Builder
@Data
public class ReleaseBundleSearchFilter {

    private BundleType bundleType;
    private String name;
    private List<String> versions;
    private long before;
    private long after;
    private long limit;
    private long offset;
    private long daoLimit;
    private String orderBy;
    private String direction;

    public enum OrderByColumn {
        NAME, VERSION, STORING_REPO, TYPE, SIGNATURE, STATUS, DATE_CREATED;

        public static String forValue(String column) {
            try {
                return OrderByColumn.valueOf(column.toUpperCase()).name().toLowerCase();
            } catch (IllegalArgumentException e) {
                return OrderByColumn.DATE_CREATED.name().toLowerCase();
            }
        }
    }

    public enum Direction {
        ASC, DESC;

        public static String forValue(String column) {
            try {
                return Direction.valueOf(column.toUpperCase()).name().toLowerCase();
            } catch (IllegalArgumentException e) {
                return Direction.DESC.name().toLowerCase();
            }
        }
    }

    public long getDaoLimit() {
        if (daoLimit == 0) {
            return ConstantValues.searchUserSqlQueryLimit.getLong();
        }
        return daoLimit;
    }

    public void setDaoLimit(long limit) {
        this.daoLimit = Long.min(limit, this.limit);
    }
}