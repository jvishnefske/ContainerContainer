package org.artifactory.ui.rest.resource.utils;

import lombok.experimental.UtilityClass;

@UtilityClass
public class AutoCompleteConstants {
    public static final String AUTO_COMPLETE_SEARCH_PARAM = "search";
    public static final String AUTO_COMPLETE_REPO_TYPE_PARAM = "repoType";
    public static final String AUTO_COMPLETE_PACKAGE_TYPE_PARAM = "packageType";
    public static final String AUTO_COMPLETE_LIMIT_PARAM = "limit";
    public static final String AUTO_COMPLETE_CONTINUE_STATE_PARAM = "continueState";
    public static final String AUTO_COMPLETE_PROJECTS_PARAM = "projects";
    public static final String AUTO_COMPLETE_STAGES_PARAM = "stages";
    public static final String AUTO_COMPLETE_LIMIT_DEFAULT_VALUE = "50";
    public static final Long AUTO_COMPLETE_CONTINUE_STATE_INIT_VALUE = 0L;
}
