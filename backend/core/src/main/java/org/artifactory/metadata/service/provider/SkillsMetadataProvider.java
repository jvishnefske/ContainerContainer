package org.artifactory.metadata.service.provider;

import org.artifactory.descriptor.repo.RepoType;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * @author Shahaf Golan
 */
@Component
public class SkillsMetadataProvider extends AbstractSimplePrefixMetadataProvider {

    private static final String SKILL_PREFIX = "skill";
    private static final String OS_KEY = SKILL_PREFIX + ".os";
    private static final String LEAD_FILE_EXTENSION = "zip";

    SkillsMetadataProvider() {
        super(RepoType.Skills, SKILL_PREFIX, SKILL_PREFIX + TypeSpecificMetadataTemplate.NAME_SUFFIX,
                LEAD_FILE_EXTENSION, Set.of(OS_KEY));
    }
}
