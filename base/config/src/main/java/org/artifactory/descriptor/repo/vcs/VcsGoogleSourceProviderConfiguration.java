package org.artifactory.descriptor.repo.vcs;


public class VcsGoogleSourceProviderConfiguration extends VcsProviderConfiguration {

    public VcsGoogleSourceProviderConfiguration() {
        super(
                "GoogleSource",
                "",
                "{0}/{1}/+archive/refs/{2}.{3}",
                "{0}/{1}/+/refs/heads/{3}/{2}"
        );
    }
}
