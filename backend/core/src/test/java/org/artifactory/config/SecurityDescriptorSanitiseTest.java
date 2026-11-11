package org.artifactory.config;

import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.config.CentralConfigDescriptorImpl;
import org.artifactory.descriptor.retention.ArtifactRetentionPolicyDescriptor;
import org.artifactory.descriptor.security.PasswordExpirationPolicy;
import org.artifactory.descriptor.security.PasswordSettings;
import org.artifactory.descriptor.security.SecurityDescriptor;
import org.artifactory.descriptor.security.signingkeys.SigningKeysSettings;
import org.artifactory.descriptor.security.sshserver.SshServerSettings;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.mockito.Mock;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.List;

import static org.artifactory.config.CentralConfigDescriptorSanitizer.merge;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;

/**
 * @author igoru
 */
public class SecurityDescriptorSanitiseTest {

    private CentralConfigDescriptorImpl source;
    private SecurityDescriptor sourceSecurity;
    private CentralConfigDescriptorImpl target;
    private SecurityDescriptor targetSecurity;

    @Mock
    private ArtifactoryApplicationContext applicationContext;
    @Mock
    private RepositoryService repositoryService;
    private AutoCloseable mocks;

    @BeforeMethod
    public void setUp() {
        mocks = openMocks(this);
        when(applicationContext.beanForType(RepositoryService.class)).thenReturn(repositoryService);
        when(repositoryService.getLocalRepoConfigsExcludingInfoRepos()).thenReturn(List.of());

        source = new CentralConfigDescriptorImpl();
        sourceSecurity = source.getSecurity();
        target = new CentralConfigDescriptorImpl();
        targetSecurity = target.getSecurity();

        ArtifactoryContextThreadBinder.bind(applicationContext);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    public void takesHideUnauthorizedResourcesFromSource() {
        sourceSecurity.setHideUnauthorizedResources(true);
        targetSecurity.setHideUnauthorizedResources(false);

        SecurityDescriptor result = merge(source, target).getSecurity();

        assertTrue(result.isHideUnauthorizedResources());
    }

    @Test
    public void takesPasswordSettingsFromTarget() {
        PasswordSettings sourceSettings = new PasswordSettings();
        PasswordExpirationPolicy sourcePolicy = new PasswordExpirationPolicy();
        sourcePolicy.setPasswordMaxAge(42);
        sourceSettings.setExpirationPolicy(sourcePolicy);
        sourceSecurity.setPasswordSettings(sourceSettings);

        PasswordSettings targetSettings = new PasswordSettings();
        PasswordExpirationPolicy targetPolicy = new PasswordExpirationPolicy();
        targetPolicy.setPasswordMaxAge(43);
        targetSettings.setExpirationPolicy(targetPolicy);
        targetSecurity.setPasswordSettings(targetSettings);

        SecurityDescriptor result = merge(source, target).getSecurity();

        assertEquals(result.getPasswordSettings().getExpirationPolicy().getPasswordMaxAge().longValue(), 43);
    }

    @Test
    public void takesBuildGlobalBasicReadAllowedFromSource() {
        sourceSecurity.setBuildGlobalBasicReadAllowed(true);
        targetSecurity.setBuildGlobalBasicReadAllowed(false);

        SecurityDescriptor result = merge(source, target).getSecurity();

        assertTrue(result.isBuildGlobalBasicReadAllowed());
    }

    @Test
    public void takesBuildGlobalBasicReadForAnonymousFromSource() {
        sourceSecurity.setBuildGlobalBasicReadForAnonymous(true);
        targetSecurity.setBuildGlobalBasicReadForAnonymous(false);

        SecurityDescriptor result = merge(source, target).getSecurity();

        assertTrue(result.isBuildGlobalBasicReadForAnonymous());
    }

    @Test
    public void takesSshServerSettingsFromTarget() {
        SshServerSettings sourceSettings = new SshServerSettings();
        sourceSettings.setSshServerPort(42);
        sourceSecurity.setSshServerSettings(sourceSettings);

        SshServerSettings targetSettings = new SshServerSettings();
        targetSettings.setSshServerPort(43);
        targetSecurity.setSshServerSettings(targetSettings);

        SecurityDescriptor result = merge(source, target).getSecurity();

        assertEquals(result.getSshServerSettings().getSshServerPort(), 43);
    }

    @Test
    public void takesSigningKeysSettingsFromSource() {
        SigningKeysSettings sourceSettings = new SigningKeysSettings();
        sourceSettings.setPassphrase("source");
        sourceSecurity.setSigningKeysSettings(sourceSettings);

        SigningKeysSettings targetSettings = new SigningKeysSettings();
        targetSettings.setPassphrase("target");
        targetSecurity.setSigningKeysSettings(targetSettings);

        SecurityDescriptor result = merge(source, target).getSecurity();

        assertEquals(result.getSigningKeysSettings().getPassphrase(), "source");
    }

    @Test
    public void takeRetentionPoliciesFromSource() {
        ArtifactRetentionPolicyDescriptor retentionPolicy = new ArtifactRetentionPolicyDescriptor();
        retentionPolicy.setKey("retention-policy");
        source.addRetentionPolicy(retentionPolicy);

        CentralConfigDescriptor result = merge(source, target);
        assertEquals(Collections.frequency(result.getRetentionPolicies(), retentionPolicy), 1);

        result = merge(source, target);
        assertEquals(Collections.frequency(result.getRetentionPolicies(), retentionPolicy), 1);
    }
}
