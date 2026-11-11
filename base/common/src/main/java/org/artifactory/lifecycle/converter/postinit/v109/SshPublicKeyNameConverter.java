package org.artifactory.lifecycle.converter.postinit.v109;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.SecurityService;
import org.artifactory.api.security.UserGroupService;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.converter.ConverterPreconditionException;
import org.artifactory.converter.postinit.PostInitConverter;
import org.artifactory.security.UserInfo;
import org.artifactory.version.ArtifactoryServiceVersion;
import org.artifactory.version.CompoundVersionDetails;

import static org.artifactory.version.ArtifactoryServiceVersionProvider.v7635;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v7800;

@Slf4j
public class SshPublicKeyNameConverter extends PostInitConverter {
    private static final String SSH_PUBLIC_KEY = "sshPublicKey";
    private static final String DEFAULT_SSH_PUBLIC_KEY = "default-sshPublicKey";
    public SshPublicKeyNameConverter(ArtifactoryServiceVersion from, ArtifactoryServiceVersion until) {
        super(from, until);
    }
    @Override
    public boolean isInterested(CompoundVersionDetails source, CompoundVersionDetails target) {
        return source.getVersion().afterOrEqual(v7635.get()) && !source.getVersion().after(v7800.get());
    }
    @Override
    public void convert(CompoundVersionDetails source, CompoundVersionDetails target) {
        SecurityService securityService = ContextHelper.get().beanForType(SecurityService.class);
        securityService.doAsSystem(this::convert);
    }
    private void convert() {
        long startTime = System.currentTimeMillis();
        log.info("Starting SSH Public Key Name Converter converter {}" ,startTime);
        UserGroupService userGroupService = ContextHelper.get().beanForType(UserGroupService.class);
        userGroupService.getAllUsers(true).stream()
                .filter(SshPublicKeyNameConverter::isUserWithAMissingNewSshKeyProperty)
                .forEach(userInfo -> addUserSshCustomData(userGroupService, userInfo));
        long totalTime = System.currentTimeMillis() - startTime;
        log.info("End converting SSH Public Key Names within {} milliseconds." ,totalTime);
    }

    private static boolean isUserWithAMissingNewSshKeyProperty(UserInfo userInfo) {
        return userInfo.getUserProperty(SSH_PUBLIC_KEY).isPresent() &&
                // DEFAULT_SSH_PUBLIC_KEY could be added during fallback during authentication
                userInfo.getUserProperty(DEFAULT_SSH_PUBLIC_KEY).isEmpty();
    }

    private void addUserSshCustomData(UserGroupService userGroupService, UserInfo userInfo){
        log.info("Adding new SSH Public Keys");
        userInfo.getUserProperty(SSH_PUBLIC_KEY).ifPresent(key -> {
            userGroupService.addUserProperty(userInfo.getUsername(), DEFAULT_SSH_PUBLIC_KEY, key);
            userGroupService.addUserProperty(userInfo.getUsername(), DEFAULT_SSH_PUBLIC_KEY + "-date",
                    String.valueOf(System.currentTimeMillis()));
        });
    }
    @Override
    public void assertConversionPrecondition(ArtifactoryHome home, CompoundVersionDetails fromVersion,
            CompoundVersionDetails toVersion) throws ConverterPreconditionException {
        // not used
    }
}