package org.artifactory.projects;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.security.SecurityService;
import org.artifactory.config.CentralConfigDiffValidation;
import org.artifactory.config.CentralConfigKey;
import org.jfrog.common.config.diff.DataDiff;
import org.jfrog.common.config.diff.DiffUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * @author BarakH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectAdminValidation implements CentralConfigDiffValidation {

    private final SecurityService securityService;

    private final Set<String> approvedConfigChangesForProjectAdmin = Set.of(CentralConfigKey.repoLayouts.getKey(),
                    CentralConfigKey.backups.getKey(),
                    CentralConfigKey.localReplications.getKey(),
                    CentralConfigKey.remoteReplications.getKey());


    public void validate(List<DataDiff<?>> diffResult) throws IllegalArgumentException {
        log.debug("Checking if project admin (or has MC scope) " +
                "is allowed to make those Artifactory configuration changes");
        if (securityService.isAdmin() || securityService.hasMcScope()) {
            return;
        }
        for (DataDiff<?> diff : diffResult) {
            String field = getFieldName(diff);
            if (!approvedConfigChangesForProjectAdmin.contains(field)) {
                throw new IllegalArgumentException(
                        "Project admin is not allowed to make those Artifactory configuration changes.");
            }
        }
    }

    @NonNull
    private String getFieldName(DataDiff<?> diff) {
        String field = diff.getFieldName();
        if (StringUtils.isEmpty(field)) {
            return StringUtils.EMPTY;
        }
        if (field.contains(DiffUtils.DELIMITER)) {
            field = field.substring(0, field.indexOf(DiffUtils.DELIMITER));
        }
        return field;
    }
}
