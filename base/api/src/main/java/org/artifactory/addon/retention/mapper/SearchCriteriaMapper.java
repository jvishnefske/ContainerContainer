package org.artifactory.addon.retention.mapper;

import org.artifactory.addon.retention.ArtifactRetentionCandidateCriteria;
import org.artifactory.addon.retention.ArtifactRetentionPolicyFieldCriteriaModel;
import org.artifactory.addon.retention.PackageRetentionCandidateCriteria;
import org.artifactory.descriptor.retention.search.criteria.ArtifactRetentionPolicySearchCriteriaDescriptor;
import org.artifactory.descriptor.retention.search.criteria.PackageSearchCriteriaDescriptor;
import org.artifactory.mapper.ArtifactoryCentralMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.ReportingPolicy;

/**
 * Two-way mapper from the {@link ArtifactRetentionPolicyFieldCriteriaModel} to the
 * {@link ArtifactRetentionPolicySearchCriteriaDescriptor} and from the
 * {@link PackageRetentionCandidateCriteria} to the {@link PackageSearchCriteriaDescriptor}
 *
 * @author Tamar Rosen
 */
@Mapper(config = ArtifactoryCentralMapperConfig.class, nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
        componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SearchCriteriaMapper {

    ArtifactRetentionPolicySearchCriteriaDescriptor searchCriteriaModelToDescriptor(
            ArtifactRetentionCandidateCriteria searchCriteriaModel);

    ArtifactRetentionCandidateCriteria searchCriteriaDescriptorToModel(
            ArtifactRetentionPolicySearchCriteriaDescriptor searchCriteriaDescriptor);

    PackageSearchCriteriaDescriptor packageSearchCriteriaModelToDescriptor(
            PackageRetentionCandidateCriteria packageSearchCriteriaModel);

    PackageRetentionCandidateCriteria packageSearchCriteriaDescriptorToModel(
            PackageSearchCriteriaDescriptor packageSearchCriteriaDescriptor);
}
