package org.artifactory.repo.interceptor;

/**
 * Classification of what initiated a repo CUD (create/update/delete) operation.
 * The enum name is the wire value placed on repo CUD event {@code userContext.triggerSource}.
 */
public enum RepoEventTriggerSource {

    /** Action performed in the Artifactory web UI. */
    JFROG_UI,

    /** JFrog CLI, including GitHub Actions setup-jfrog-cli usage. */
    JFROG_CLI,

    /** JFrog Terraform provider. */
    TERRAFORM,

    /** Direct API call from an unidentified client. */
    REST_API,

    /** Non-human, platform-initiated execution such as system/default repos, import, or federation sync. */
    SYSTEM_INTERNAL,

    /** AI agent via the JFrog MCP server or JFrog AI agent context. */
    JFROG_AI_AGENT,

    /** AI agent via a non-JFrog MCP server or generic AI agent tooling. */
    USER_AI_AGENT,

    /** No classifiable signal. */
    UNKNOWN;

    public boolean isAiAgent() {
        return this == JFROG_AI_AGENT || this == USER_AI_AGENT;
    }
}
