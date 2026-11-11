package org.artifactory.ui.rest.service.setmeup;

import org.springframework.beans.factory.annotation.Lookup;
import org.springframework.stereotype.Component;

@Component
public abstract class SetMeUpServiceFactory {

    @Deprecated(forRemoval = true)
    @Lookup
    public abstract GetSetMeUpService getLegacySetMeUp();

    @Lookup
    public abstract GetAllReposByPackageHandler getAllReposByPackageHandler();

    @Lookup
    public abstract GetAllPackagesHandler getAllPackagesHandler();

    @Lookup
    public abstract GetRepoHandler getRepoHandler();

    @Lookup
    public abstract MavenSettingGeneratorService mavenSettingGenerator();

    @Lookup
    public abstract GradleSettingGeneratorService gradleSettingGenerator();

    @Lookup
    public abstract IvySettingGeneratorService ivySettingGenerator();

    @Lookup
    public abstract GetMavenSettingSnippetService getMavenSettingSnippet();

    @Lookup
    public abstract GetGradleSettingSnippetService getGradleSettingSnippet();

    @Lookup
    public abstract GetIvySettingSnippetService getIvySettingSnippet();

    @Lookup
    public abstract GetReverseProxySetMeUpDataService getReverseProxySetMeUpData();

    @Lookup
    public abstract GetMavenDistributionMgntService getMavenDistributionMgnt();

    @Lookup
    public abstract GetPublicKeyFilenamePerRepoService getPublicKeyFilenamePerRepoService();

    @Lookup
    public abstract GetNixTrustedPublicKeyPerRepoService getNixTrustedPublicKeyPerRepoService();

    @Lookup
    public abstract GetTerraformConfigSnippetService getTerraformConfigSnippetService();

    @Lookup
    public abstract GetReposByKeysHandler getReposByKeysHandler();

    @Lookup
    public abstract AIEditorExtensionGenerateTokenHandler getAIEditorExtensionGenerateTokenHandler();
}