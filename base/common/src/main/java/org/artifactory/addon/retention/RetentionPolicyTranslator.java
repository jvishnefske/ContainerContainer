package org.artifactory.addon.retention;

import lombok.experimental.UtilityClass;
import org.artifactory.addon.retention.mapper.SearchCriteriaMapper;
import org.artifactory.descriptor.retention.ArtifactRetentionOperationType;
import org.artifactory.descriptor.retention.ArtifactRetentionPolicyDescriptor;
import org.mapstruct.factory.Mappers;

import java.util.Objects;

@UtilityClass
public class RetentionPolicyTranslator {
    private static final SearchCriteriaMapper searchCriteriaMapper
            = Mappers.getMapper(SearchCriteriaMapper.class);

    public ArtifactRetentionPolicyModel toPolicyModel(ArtifactRetentionPolicyDescriptor policyDescriptor) {
        return ArtifactRetentionPolicyModel.builder()
                .key(policyDescriptor.getKey())
                .enabled(policyDescriptor.isEnabled())
                .description(policyDescriptor.getDescription())
                .cronExp(policyDescriptor.getCronExp())
                .aqlQuery(policyDescriptor.getAqlQuery())
                .durationInMinutes(policyDescriptor.getDurationInMinutes())
                .expirationTimeInMonths(policyDescriptor.getExpirationTimeInMonths())
                .skipTrashcan(policyDescriptor.isSkipTrashcan())
                .packageRetention(policyDescriptor.isPackageRetention() ? true : null)
                .searchCriteriaForm(
                        searchCriteriaMapper.searchCriteriaDescriptorToModel(policyDescriptor.getSearchCriteriaForm()))
                .packageSearchCriteriaForm(searchCriteriaMapper.packageSearchCriteriaDescriptorToModel(
                        policyDescriptor.getPackageSearchCriteriaForm()))
                .build();
    }

    public ArtifactRetentionPolicyDescriptor toPolicyDescriptor(ArtifactRetentionPolicyBaseModel policyModelBase,
            ArtifactRetentionOperationType type) {
        ArtifactRetentionPolicyDescriptor policyDescriptor = new ArtifactRetentionPolicyDescriptor();
        policyDescriptor.setKey(policyModelBase.getKey());
        policyDescriptor.setEnabled(policyModelBase.isEnabled());
        policyDescriptor.setDescription(policyModelBase.getDescription());
        policyDescriptor.setCronExp(policyModelBase.getCronExp());
        policyDescriptor.setAqlQuery(policyModelBase.getAqlQuery());
        policyDescriptor.setType(type.name());
        long policyDuration =
                Objects.isNull(policyModelBase.getDurationInMinutes()) ? 0 : policyModelBase.getDurationInMinutes();
        policyDescriptor.setDurationInMinutes(policyDuration);
        long expirationTime = Objects.isNull(policyModelBase.getExpirationTimeInMonths()) ? 0 :
                policyModelBase.getExpirationTimeInMonths();
        policyDescriptor.setExpirationTimeInMonths(expirationTime);
        policyDescriptor.setSkipTrashcan(policyModelBase.isSkipTrashcan());
        policyDescriptor.setPackageRetention(Boolean.TRUE.equals(policyModelBase.getPackageRetention()));
        policyDescriptor.setSearchCriteriaForm(
                searchCriteriaMapper.searchCriteriaModelToDescriptor(policyModelBase.getSearchCriteriaForm()));
        policyDescriptor.setPackageSearchCriteriaForm(
                searchCriteriaMapper.packageSearchCriteriaModelToDescriptor(
                        policyModelBase.getPackageSearchCriteriaForm()));
        return policyDescriptor;
    }
}
