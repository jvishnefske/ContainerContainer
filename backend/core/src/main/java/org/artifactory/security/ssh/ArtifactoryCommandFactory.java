/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.security.ssh;

import org.apache.commons.lang3.StringUtils;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.command.CommandFactory;
import org.apache.sshd.server.shell.UnknownCommand;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.security.ssh.command.CliAuthenticateCommand;
import org.artifactory.security.ssh.command.GitLfsAuthenticateCommand;
import org.artifactory.storage.security.service.UserGroupStoreService;

/**
 * @author Noam Y. Tenne
 * @author Chen Keinan
 */
public class ArtifactoryCommandFactory implements CommandFactory {

    private final CentralConfigService centralConfigService;
    private final UserGroupStoreService userGroupStoreService;
    private final ArtifactoryHome artifactoryHome;
    private final ArtifactoryContext artifactoryContext;

    public ArtifactoryCommandFactory(CentralConfigService centralConfigService,
            UserGroupStoreService userGroupStoreService,
            ArtifactoryHome artifactoryHome,
            ArtifactoryContext artifactoryContext) {
        this.centralConfigService = centralConfigService;
        this.userGroupStoreService = userGroupStoreService;
        this.artifactoryHome = artifactoryHome;
        this.artifactoryContext = artifactoryContext;
    }

    @Override
    public Command createCommand(ChannelSession var1, String command) {
        if (isGitLfsCommand(command)) {
            return new GitLfsAuthenticateCommand(centralConfigService, userGroupStoreService, artifactoryHome,
                    artifactoryContext, command);
        }
        if (isCliCommand(command)) {
            return new CliAuthenticateCommand(centralConfigService, userGroupStoreService, artifactoryHome,
                    artifactoryContext, command);
        }
        return new UnknownCommand(command);
    }

    private boolean isCliCommand(String command) {
        return CliAuthenticateCommand.COMMAND_NAME.startsWith(command);
    }

    private boolean isGitLfsCommand(String command) {
        if (StringUtils.isNotEmpty(command)) {
            return command.startsWith(GitLfsAuthenticateCommand.COMMAND_NAME);
        }
        return false;
    }
}
