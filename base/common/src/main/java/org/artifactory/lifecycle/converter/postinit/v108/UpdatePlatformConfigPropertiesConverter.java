package org.artifactory.lifecycle.converter.postinit.v108;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.converter.ConverterPreconditionException;
import org.artifactory.converter.postinit.PostInitConverter;
import org.artifactory.platform.PlatformGeneralConfig;
import org.artifactory.platform.PlatformSystemMessageConfig;
import org.artifactory.security.access.AccessService;
import org.artifactory.version.ArtifactoryServiceVersion;
import org.artifactory.version.CompoundVersionDetails;
import org.jfrog.access.client.platformconfig.ConfigurationModel;
import org.jfrog.access.client.platformconfig.GrpcPlatformConfigClient;

import static org.artifactory.platform.PlatformConfigHolder.PLATFORM_GENERAL_CONFIG_KEY;
import static org.artifactory.platform.PlatformConfigHolder.PLATFORM_SYSTEM_MESSAGE_CONFIG_KEY;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v7590m001;

/**
 * @author Ohad Hageby
 *
 * Taking care of old properties in the format: "general.*" or 'system.message.*"
 * by adding the prefix "platform.config." and saving back to access client config.
 */
@Slf4j
public class UpdatePlatformConfigPropertiesConverter extends PostInitConverter {
    final static String OLD_GENERAL_PLATFORM_KEY = "general";
    final static String OLD_SYSTEM_PLATFORM_KEY = "system.message";
    private GrpcPlatformConfigClient platformConfigClient;

    public UpdatePlatformConfigPropertiesConverter(ArtifactoryServiceVersion from, ArtifactoryServiceVersion until) {
        super(from, until);
        AccessService accessService = ContextHelper.get().beanForType(AccessService.class);
        platformConfigClient = accessService.getPlatformConfigClient();
    }

    /**
     * @param home
     * @param fromVersion
     * @param toVersion
     * @throws ConverterPreconditionException
     */
    @Override
    public void assertConversionPrecondition(ArtifactoryHome home, CompoundVersionDetails fromVersion,
            CompoundVersionDetails toVersion) throws ConverterPreconditionException {
        //nop
    }

    @Override
    public void convert(CompoundVersionDetails source, CompoundVersionDetails target) {

        ConfigurationModel cmGeneral = platformConfigClient.getConfig(OLD_GENERAL_PLATFORM_KEY,
                PlatformGeneralConfig.class).orElse(null);

        ConfigurationModel cmSystem = platformConfigClient.getConfig(OLD_SYSTEM_PLATFORM_KEY,
                PlatformSystemMessageConfig.class).orElse(null);

        if (cmGeneral != null) {
            platformConfigClient.updateConfig(PLATFORM_GENERAL_CONFIG_KEY,
                    oldConfig -> cmGeneral, (Class<ConfigurationModel>) cmGeneral.getClass(), false);
            log.info("Converting 'general.*' configuration properties to {} Found existing configuration: {}" ,
                    PLATFORM_GENERAL_CONFIG_KEY, cmGeneral.toString());
        }

        if (cmSystem != null) {
            platformConfigClient.updateConfig(PLATFORM_SYSTEM_MESSAGE_CONFIG_KEY,
                        oldConfig -> cmSystem, (Class<ConfigurationModel>) cmSystem.getClass(), false);
            log.info("Converting 'system.message.*' configuration properties to {} Found existing configuration: {}",
                    PLATFORM_SYSTEM_MESSAGE_CONFIG_KEY, cmSystem.toString());
        }
    }

    /**
     * The default shouldConvert logic is a bit special as we usually want post init conversion to only run if upgrading
     * from an impacted version.
     * For instance, the docker conversion fixes a bug that was introduced in 4.11.0 and solved in 4.12.1 so conversion
     * should run only when upgrading from versions that are between these 2.
     * (meaning that if an upgrade is 4.9 -> 4.13 conversion will not run in this case)
     * <p>
     * Still this method may be overloaded by the implementing class to allow more flexibility over when to run.
     *
     * @param source
     * @param target
     */
    @Override
    public boolean isInterested(CompoundVersionDetails source, CompoundVersionDetails target) {
        return source.getVersion().before(v7590m001.get()) && target.getVersion().afterOrEqual(v7590m001.get());
    }
}
