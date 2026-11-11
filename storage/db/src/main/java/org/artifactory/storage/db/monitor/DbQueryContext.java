package org.artifactory.storage.db.monitor;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.activity.ActivityContext;
import org.artifactory.activity.CurrentActivity;

/**
 * Holds properties of a database query, such as a SQL text, and details of the activity that launched it.
 * Used in monitoring of database queries.
 *
 * @author Sergey Steinvil
 */
@Getter
@AllArgsConstructor
public class DbQueryContext {
    private final String query;
    private final boolean isStreaming;
    private final String hint;
    private final boolean changedConnectionAutoCommit;
    private final String actionName;
    private final String actionKind;
    private final String actionGroup;
    private final String actionPackage;
    private final String actionActor;

    public DbQueryContext(String query, boolean isStreaming, String hint) {
        this(query, isStreaming, hint, false);
    }

    public DbQueryContext(String query, boolean isStreaming, String hint, boolean changedConnectionAutoCommit) {
        this(query, isStreaming, hint, changedConnectionAutoCommit, CurrentActivity.context());
    }

    public DbQueryContext(String query, boolean isStreaming, String hint,
            boolean changedConnectionAutoCommit, ActivityContext activity) {
        this(query, isStreaming, hint, changedConnectionAutoCommit, activity.getName(), activity.getKind(),
                activity.getGroup(), activity.getPackageType(), activity.getActor());
    }

    /**
     * Computes a stable route key that identifies the route family of this query.
     *
     * <ol>
     *   <li>For Jersey REST requests the URI template is stored in {@code actionName}
     *       (e.g. {@code search/aql}), which is the most precise identifier.</li>
     *   <li>For non-Jersey (RepoFilter) requests both the route family
     *       ({@code actionGroup}) and the package type ({@code actionPackage}) are combined
     *       into a human-readable key (e.g. {@code download npm}).</li>
     *   <li>When only the group is available (background jobs, etc.), the group is returned
     *       alone (e.g. {@code download}).</li>
     *   <li>When no contextual information is available, {@code "unknown"} is returned.</li>
     * </ol>
     *
     * The returned value mirrors what {@code UniversalLimitHandlerUtility#getContextInfo()} returns
     * for the same request thread, so violation records can be persisted and matched symmetrically.
     */
    public String getRouteKey() {
        if (StringUtils.isNotBlank(actionName)) {
            return actionName;
        }
        if (StringUtils.isNotBlank(actionGroup)) {
            return StringUtils.isNotBlank(actionPackage)
                    ? actionGroup + " " + actionPackage
                    : actionGroup;
        }
        return "unknown";
    }
}
