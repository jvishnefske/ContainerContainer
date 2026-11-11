package org.artifactory.lifecycle.converter.postinit.v110;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.SecurityService;
import org.artifactory.api.security.UserGroupService;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.converter.ConverterPreconditionException;
import org.artifactory.converter.postinit.PostInitConverter;
import org.artifactory.storage.fs.service.ConfigsService;
import org.artifactory.storage.fs.service.ConfigsServiceKey;
import org.artifactory.version.ArtifactoryServiceVersion;
import org.artifactory.version.CompoundVersionDetails;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v7635;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v7800;

@Slf4j
public class DeleteSshPublicOldNameConverter extends PostInitConverter {
    private static final String SSH_PUBLIC_KEY = "sshPublicKey";
    public DeleteSshPublicOldNameConverter(ArtifactoryServiceVersion from, ArtifactoryServiceVersion until) {
        super(from, until);
    }

    @Override
    public boolean isInterested(CompoundVersionDetails source, CompoundVersionDetails target) {
        log.info("DeleteSshPublicOldNameConverter source version : {} , target version : {}" ,
                source.getVersion(), target.getVersion());
        return source.getVersion().afterOrEqual(v7635.get()) && !source.getVersion().after(v7800.get());
    }

    @Override
    public void convert(CompoundVersionDetails source, CompoundVersionDetails target) {
        if (shouldRunDeleteSshConverter()) {
            SecurityService securityService = ContextHelper.get().beanForType(SecurityService.class);
            securityService.doAsSystem(this::convert);
        }
    }
    private void convert() {
        long startTime = System.currentTimeMillis();
        log.info("Start deleting old SSH keys names");
        UserGroupService userGroupService = ContextHelper.get().beanForType(UserGroupService.class);
        userGroupService.deletePropertyFromAllUsers(SSH_PUBLIC_KEY);
        long totalTime = System.currentTimeMillis() - startTime;
        log.info("End deleting SSH Public Key old Names within {} milliseconds." ,totalTime);
    }

    @Override
    public void assertConversionPrecondition(ArtifactoryHome home, CompoundVersionDetails fromVersion,
            CompoundVersionDetails toVersion) throws ConverterPreconditionException {
        // not in use
    }

    private boolean shouldRunDeleteSshConverter(){
        log.info("DeleteSshPublicOldNameConverter run check");
        ConfigsService configsService =  ContextHelper.get().beanForType(ConfigsService.class);
        String converterStatus =
                configsService.getConfig(ConfigsServiceKey.ARTIFACTORY_CONVERTER_DELETE_OLD_SSH_KEYS);
        if (isNotBlank(converterStatus)){
            log.info("Converter already ran, no need to ran again. {}", converterStatus);
            return false;
        }
        configsService.addConfig(ConfigsServiceKey.ARTIFACTORY_CONVERTER_DELETE_OLD_SSH_KEYS.getKeyName(),
                Boolean.toString(true),
                System.currentTimeMillis());
        log.info("Successfully updated DB configs table for migration finalized.");
        return true;
    }
}
