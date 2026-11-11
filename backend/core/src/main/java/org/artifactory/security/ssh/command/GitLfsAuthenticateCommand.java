/*
 *
 * Copyright 2018 JFrog Ltd. All rights reserved.
 * JFROG PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package org.artifactory.security.ssh.command;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHeaders;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.security.access.CreatedTokenInfo;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.security.ssh.UsernameAttributeKey;
import org.artifactory.security.ssh.model.GitLfsCommandDetails;
import org.artifactory.storage.security.service.UserGroupStoreService;
import org.jfrog.client.util.PathUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.artifactory.security.props.auth.OauthManager.OAUTH_TOKEN_PREFIX;

/**
 * @author Noam Y. Tenne
 * @author Chen Keinan
 */
@Slf4j
public class GitLfsAuthenticateCommand extends AbstractAuthenticateCommand {

    public static final String COMMAND_NAME = "git-lfs-authenticate";

    private static final Pattern COMMAND_PATTERN = Pattern
            .compile("git-lfs-authenticate /?artifactory/([^\\s]+) ([^\\s]+)( [^\\s]*)?");

    private GitLfsCommandDetails command;

    public GitLfsAuthenticateCommand(CentralConfigService centralConfigService,
            UserGroupStoreService userGroupStoreService, ArtifactoryHome artifactoryHome,
            ArtifactoryContext artifactoryContext, String command) {
        super(centralConfigService, userGroupStoreService, artifactoryHome, artifactoryContext, command);
    }

    protected void parseCommandDetails(String command) {
        Matcher matcher = COMMAND_PATTERN.matcher(command);
        if (!matcher.matches()) {
            log.debug("Unable to parse git-lfs command {}", command);
            throw new IllegalArgumentException("Unable to parse git-lfs command " + command);
        }
        String blob = matcher.group(3);
        if (blob != null) {
            blob = blob.substring(1); // remove leading space
        }
        this.command = new GitLfsCommandDetails(matcher.group(1), matcher.group(2), blob);
    }

    protected void sendAuthHeader() throws IOException {
        Map<String, Object> response = new HashMap<>();
        Map<String, Object> header = new HashMap<>();
        setResponseHeaderAndData(response, header);
        // write response
        writeResponse(response, header);
    }

    /**
     * set response header and data
     *
     * @param response - response map
     * @param header   - response header map
     */
    private void setResponseHeaderAndData(Map<String, Object> response, Map<String, Object> header) {
        String urlBase = getBaseUrl();
        response.put(HREF, PathUtils.addTrailingSlash(urlBase) + "api/lfs/" + command.repoKey());
        String username = serverSession.getAttribute(new UsernameAttributeKey());
        CreatedTokenInfo tokenInfo = generateUniqueToken(username);
        header.put(HttpHeaders.AUTHORIZATION, OAUTH_TOKEN_PREFIX + tokenInfo.getTokenValue());
    }
}
