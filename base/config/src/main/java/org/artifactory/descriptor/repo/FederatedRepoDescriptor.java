package org.artifactory.descriptor.repo;

import com.google.common.collect.Lists;
import jakarta.annotation.Nonnull;
import jakarta.validation.Valid;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlIDREF;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import org.artifactory.descriptor.Descriptor;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.property.PropertySet;
import org.artifactory.model.BaseConfig;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.FederatedRepositoryRepoTypeConfig;
import org.artifactory.model.SecurityConfig;
import org.jfrog.common.config.diff.DiffAtomic;
import org.jfrog.common.config.diff.DiffReference;
import org.jfrog.common.config.diff.GenerateDiffFunction;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author Gidi Shabat
 */
@XmlType(name = "FederatedRepoType", propOrder = {"federatedMembers", "proxy", "disableProxy",
        "modificationDate", "maxNumberOfThreads"},
        namespace = Descriptor.NS)
@GenerateDiffFunction
@XmlRootElement
public class FederatedRepoDescriptor extends LocalRepoDescriptor implements ProxyRepoDescriptor {

    @XmlElement(name = "federatedMember")
    @XmlElementWrapper(name = "federatedMembers")
    @DiffAtomic
    private List<FederatedMember> federatedMembers = Lists.newArrayList();

    @XmlIDREF
    @XmlElement(name = "proxyRef")
    @DiffReference
    @Valid
    private ProxyDescriptor proxy;

    private boolean disableProxy;

    private long modificationDate;

    @XmlElement
    private Integer maxNumberOfThreads;

    public FederatedRepoDescriptor() {
    }

    public FederatedRepoDescriptor(FederatedRepoConfig repositoryConfig, List<RepoLayout> repoLayouts,
            List<PropertySet> propertySets, List<KeyPairDescriptor> keyPairs, List<ProxyDescriptor> proxies) {
        setKey(repositoryConfig.getKey());
        setType(repositoryConfig.getPackageType());
        setBaseConfig(repositoryConfig.getBaseConfig(), repoLayouts);
        setRepoTypeConfig(repositoryConfig.getRepoTypeConfig(), propertySets, proxies);
        setSecurityConfig(repositoryConfig.getSecurityConfig(), keyPairs);
        setPackageTypeConfig(repositoryConfig.getPackageTypeConfig(), repositoryConfig.getPackageType());
    }

    @Override
    public FederatedRepoConfig toRepositoryConfig() {
        FederatedRepoConfig federatedRepoConfig = FederatedRepoConfig.create(getKey(), getType());
        buildBaseConfig(federatedRepoConfig.getBaseConfig());
        buildRepoTypeConfig(federatedRepoConfig.getRepoTypeConfig());
        buildSecurityConfig(federatedRepoConfig.getSecurityConfig());
        buildPackageTypeConfig(getType(), federatedRepoConfig.getPackageTypeConfig());
        // Sync federated members and modification date to both locations
        // (baseConfig.federationConfig and repoTypeConfig). This ensures getFederatedMembers()
        // and getModificationDate() return correct data when reading from new location
        federatedRepoConfig.setFederatedMembers(
                federatedRepoConfig.getRepoTypeConfig().getFederatedMembers());
        federatedRepoConfig.setModificationDate(federatedRepoConfig.getRepoTypeConfig().getModificationDate());
        return federatedRepoConfig;
    }

    public static FederatedRepoDescriptor fromRepositoryConfig(CentralConfigDescriptor centralConfigDescriptor,
            FederatedRepoConfig sourceRepoConfig) {
        return new FederatedRepoDescriptor(sourceRepoConfig, centralConfigDescriptor.getRepoLayouts(),
                centralConfigDescriptor.getPropertySets(),
                centralConfigDescriptor.getKeyPairs(), centralConfigDescriptor.getProxies());
    }

    public Integer getMaxNumberOfThreads() {
        return maxNumberOfThreads;
    }

    public void setMaxNumberOfThreads(Integer maxNumberOfThreads) {
        this.maxNumberOfThreads = maxNumberOfThreads;
    }

    public List<FederatedMember> getFederatedMembers() {
        return federatedMembers;
    }

    public void setFederatedMembers(List<FederatedMember> federatedMembers) {
        this.federatedMembers = federatedMembers;
    }

    public long getModificationDate() {
        return modificationDate;
    }

    public void setModificationDate(long modificationDate) {
        this.modificationDate = modificationDate;
    }

    public ProxyDescriptor getProxy() {
        return proxy;
    }

    @Override
    public void setProxy(ProxyDescriptor proxy) {
        this.proxy = proxy;
    }

    public boolean isDisableProxy() {
        return disableProxy;
    }

    @Override
    public void setDisableProxy(boolean disableProxy) {
        this.disableProxy = disableProxy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FederatedRepoDescriptor)) {
            return false;
        }
        FederatedRepoDescriptor that = (FederatedRepoDescriptor) o;
        if (!Objects.equals(federatedMembers, that.federatedMembers)) {
            return false;
        }
        if (!Objects.equals(proxy, that.proxy)) {
            return false;
        }
        return modificationDate == that.modificationDate &&
                getKey().equals(that.getKey()) &&
                getType() == that.getType();
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), federatedMembers);
    }

    private void setBaseConfig(BaseConfig baseConfig, List<RepoLayout> repoLayouts) {
        setDescription(baseConfig.getDescription());
        setNotes(baseConfig.getNotes());
        for (RepoLayout repoLayout : repoLayouts) {
            if (repoLayout.getName().equals(baseConfig.getRepoLayoutRef())) {
                setRepoLayout(repoLayout);
            }
        }
        setIncludesPattern(baseConfig.getIncludesPattern());
        setExcludesPattern(baseConfig.getExcludesPattern());
        setGridTopologyId(baseConfig.getFederationConfig().getGridTopologyId());
    }

    private void buildBaseConfig(BaseConfig baseConfig) {
        baseConfig.setDescription(getDescription());
        baseConfig.setNotes(getNotes());
        if (getRepoLayout() != null) {
            baseConfig.setRepoLayoutRef(getRepoLayout().getName());
        }
        baseConfig.setIncludesPattern(getIncludesPattern());
        baseConfig.setExcludesPattern(getExcludesPattern());
        baseConfig.setGridTopologyId(getGridTopologyId());
    }


    private void setRepoTypeConfig(FederatedRepositoryRepoTypeConfig federatedConfig,
            List<PropertySet> propertySets, List<ProxyDescriptor> proxies) {
        setSnapshotVersionBehavior(
                SnapshotVersionBehaviorDescriptor.valueOf(federatedConfig.getSnapshotVersionBehavior().name()));
        setMaxUniqueSnapshots(federatedConfig.getMaxUniqueSnapshots());
        setHandleReleases(federatedConfig.isHandleReleases());
        setHandleSnapshots(federatedConfig.isHandleSnapshots());
        if (federatedConfig.getCdnRedirectRepoConfig() != null) {
            CdnRedirectRepoConfigDescriptor cdnRedirect = new CdnRedirectRepoConfigDescriptor();
            cdnRedirect.setEnabled(federatedConfig.getCdnRedirectRepoConfig().isEnabled());
            setCdnRedirectConfig(cdnRedirect);
        }
        if (federatedConfig.getDownloadRedirectConfig() != null) {
            DownloadRedirectRepoConfigDescriptor downloadRedirectRepoConfig =
                    new DownloadRedirectRepoConfigDescriptor();
            downloadRedirectRepoConfig.setEnabled(federatedConfig.getDownloadRedirectConfig().isEnabled());
            setDownloadRedirectConfig(downloadRedirectRepoConfig);
        }

        setChecksumPolicyType(
                LocalRepoChecksumPolicyTypeDescriptor.valueOf(federatedConfig.getChecksumPolicyType().name()));

        List<String> propertySetRefs = federatedConfig.getPropertySetRefs();
        ArrayList<PropertySet> newPropertySets = new ArrayList<>();
        for (String propertySetRef : propertySetRefs) {
            for (PropertySet propertySet : propertySets) {
                if (propertySet.getName().equals(propertySetRef)) {
                    newPropertySets.add(propertySet);
                }
            }
        }
        setPropertySets(newPropertySets);
        setArchiveBrowsingEnabled(federatedConfig.isArchiveBrowsingEnabled());
        setBlackedOut(federatedConfig.isBlackedOut());
        List<FederatedMember> federatedMembersDescriptors = new ArrayList<>();
        for (org.artifactory.repo.federated.FederatedMember federatedMember : federatedConfig.getFederatedMembers()) {
            FederatedMember federatedMemberDescriptor = new FederatedMember();
            federatedMemberDescriptor.setUrl(federatedMember.getUrlOrTemplate());
            federatedMemberDescriptor.setEnabled(federatedMember.isEnabled());
            federatedMemberDescriptor.setMode(federatedMember.getMode());
            federatedMembersDescriptors.add(federatedMemberDescriptor);
        }
        setFederatedMembers(federatedMembersDescriptors);
        setModificationDate(federatedConfig.getModificationDate());
        for (ProxyDescriptor proxyDescriptor : proxies) {
            if (proxyDescriptor.getKey().equals(federatedConfig.getProxyRef())) {
                setProxy(proxyDescriptor);
            }
        }
        setDisableProxy(federatedConfig.isDisableProxy());
        setPriorityResolution(federatedConfig.isPriorityResolution());
        setMaxNumberOfThreads(federatedConfig.getMaxNumberOfThreads());
    }

    protected void buildRepoTypeConfig(@Nonnull FederatedRepositoryRepoTypeConfig federatedConfig) {
        federatedConfig.setSnapshotVersionBehavior(
                org.artifactory.repo.packagetype.SnapshotVersionBehavior.valueOf(getSnapshotVersionBehavior().name()));
        federatedConfig.setMaxUniqueSnapshots(getMaxUniqueSnapshots());
        federatedConfig.setHandleReleases(isHandleReleases());
        federatedConfig.setHandleSnapshots(isHandleSnapshots());
        if (getCdnRedirectRepoConfig() != null) {
            org.artifactory.repo.CdnRedirectRepoConfig cdnRedirectRepoConfig =
                    new org.artifactory.repo.CdnRedirectRepoConfig();
            cdnRedirectRepoConfig.setEnabled(getCdnRedirectRepoConfig().isEnabled());
            federatedConfig.setCdnRedirectRepoConfig(cdnRedirectRepoConfig);
        }
        if (getDownloadRedirectConfig() != null) {
            org.artifactory.repo.DownloadRedirectRepoConfig downloadRedirectConfig =
                    new org.artifactory.repo.DownloadRedirectRepoConfig();
            downloadRedirectConfig.setEnabled(getDownloadRedirectConfig().isEnabled());
            federatedConfig.setDownloadRedirectConfig(downloadRedirectConfig);
        }
        List<String> propertySetRefs = new ArrayList<>();
        getPropertySets().forEach(propertySet -> propertySetRefs.add(propertySet.getName()));
        federatedConfig.setPropertySetRefs(propertySetRefs);
        federatedConfig.setBlackedOut(isBlackedOut());
        federatedConfig.setArchiveBrowsingEnabled(isArchiveBrowsingEnabled());
        federatedConfig.setChecksumPolicyType(
                org.artifactory.repo.local.LocalRepoChecksumPolicyType.valueOf(getChecksumPolicyType().name()));
        List<org.artifactory.repo.federated.FederatedMember> federatedMembersConfig = new ArrayList<>();
        for (FederatedMember federatedMemberDescriptor : getFederatedMembers()) {
            org.artifactory.repo.federated.FederatedMember federatedMember =
                    new org.artifactory.repo.federated.FederatedMember();
            federatedMember.setEnabled(federatedMemberDescriptor.isEnabled());
            federatedMember.setMode(federatedMemberDescriptor.getMode());
            federatedMember.setUrl(federatedMemberDescriptor.getUrlOrTemplate());
            federatedMembersConfig.add(federatedMember);
        }
        federatedConfig.setFederatedMembers(federatedMembersConfig);
        federatedConfig.setModificationDate(getModificationDate());
        if (getProxy() != null) {
            federatedConfig.setProxyRef(getProxy().getKey());
        }
        federatedConfig.setDisableProxy(isDisableProxy());
        federatedConfig.setPriorityResolution(isPriorityResolution());
        federatedConfig.setMaxNumberOfThreads(getMaxNumberOfThreads());
    }

    private void setSecurityConfig(SecurityConfig securityConfig,
            List<KeyPairDescriptor> keyPairs) {
        if (securityConfig.getXrayConfig() != null) {
            XrayRepoConfigDescriptor xrayRepoConfig = new XrayRepoConfigDescriptor();
            xrayRepoConfig.setDataTtl(securityConfig.getXrayConfig().getDataTtl());
            xrayRepoConfig.setEnabled(securityConfig.getXrayConfig().isEnabled());
            xrayRepoConfig.setXrayNotAvailableMode(securityConfig.getXrayConfig().getXrayNotAvailableMode());
            setXrayConfig(xrayRepoConfig);
        }
        setSignedUrlTtl(securityConfig.getSignedUrlTtl());

        for (KeyPairDescriptor keyPair : keyPairs) {
            if (keyPair.getKey().equals(securityConfig.getPrimaryKeyPairRef())) {
                setPrimaryKeyPairDescriptor(keyPair);
            }
            if (keyPair.getKey().equals(securityConfig.getSecondaryKeyPairRef())) {
                setSecondaryKeyPairDescriptor(keyPair);
            }
        }
    }

    @Override
    public void setForceNonDuplicateChart(boolean forceNonDuplicateChart) {
        //we want to keep the default value of false, as this feature not supported in federated repos
    }

    @Override
    public void setForceMetadataNameVersion(boolean forceMetadataNameVersion) {
        //we want to keep the default value of false, as this feature not supported in federated repos
    }
}