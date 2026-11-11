package org.artifactory.addon.retention.mapper;

import org.artifactory.addon.retention.ArtifactRetentionCandidateCriteria;
import org.artifactory.addon.retention.ArtifactRetentionPolicyFieldCriteriaModel;
import org.artifactory.addon.retention.ArtifactRetentionPolicyInclusionCriteriaModel;
import org.artifactory.addon.retention.ArtifactRetentionPolicyPropertyCriteriaModel;
import org.artifactory.descriptor.retention.search.criteria.ArtifactRetentionPolicyPathsCriteriaDescriptor;
import org.artifactory.descriptor.retention.search.criteria.ArtifactRetentionPolicyPathsInclusionCriteriaDescriptor;
import org.artifactory.descriptor.retention.search.criteria.ArtifactRetentionPolicyPropertiesCriteriaDescriptor;
import org.artifactory.descriptor.retention.search.criteria.ArtifactRetentionPolicyPropertiesInclusionCriteriaDescriptor;
import org.artifactory.descriptor.retention.search.criteria.ArtifactRetentionPolicyPropertyCriteriaDescriptor;
import org.artifactory.descriptor.retention.search.criteria.ArtifactRetentionPolicySearchCriteriaDescriptor;
import org.mapstruct.factory.Mappers;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.AssertJUnit.assertEquals;

public class SearchCriteriaMapperTest {
    private SearchCriteriaMapper mapper = Mappers.getMapper(SearchCriteriaMapper.class);

    @Test
    public void givenSearchCriteriaModelToDescriptor_whenMaps_thenCorrect() {
        ArtifactRetentionCandidateCriteria searchCriteriaModel =
                new ArtifactRetentionCandidateCriteria();
        // set created before
        searchCriteriaModel.setCreatedBefore(2L);

        // set downloaded before
        searchCriteriaModel.setDownloadedBefore(3L);

        // set properties
        ArtifactRetentionPolicyFieldCriteriaModel<ArtifactRetentionPolicyPropertyCriteriaModel> properties =
                new ArtifactRetentionPolicyFieldCriteriaModel<>();
        ArtifactRetentionPolicyPropertyCriteriaModel propertyModel = new ArtifactRetentionPolicyPropertyCriteriaModel();
        propertyModel.setPropertyKey("propKey");
        propertyModel.setPropertyValue("propValue");
        ArtifactRetentionPolicyInclusionCriteriaModel<ArtifactRetentionPolicyPropertyCriteriaModel>
                propertiesInclusionModel = new ArtifactRetentionPolicyInclusionCriteriaModel<>();
        propertiesInclusionModel.setOperator("or");
        propertiesInclusionModel.setValues(List.of(propertyModel));
        properties.setInclude(propertiesInclusionModel);
        searchCriteriaModel.setProperties(properties);
        // set repos
        ArtifactRetentionPolicyInclusionCriteriaModel<String> reposInclusionModel =
                new ArtifactRetentionPolicyInclusionCriteriaModel<>();
        reposInclusionModel.setOperator("and");

        ArtifactRetentionPolicyFieldCriteriaModel<String> repos =
                new ArtifactRetentionPolicyFieldCriteriaModel<>();
        reposInclusionModel.setValues(List.of("in-repo-1", "in-repo-2"));
        repos.setInclude(reposInclusionModel);
        searchCriteriaModel.setRepositories(repos);

        // set paths
        ArtifactRetentionPolicyInclusionCriteriaModel<String> pathsInclusionModel =
                new ArtifactRetentionPolicyInclusionCriteriaModel<>();
        pathsInclusionModel.setOperator("or");

        ArtifactRetentionPolicyFieldCriteriaModel<String> paths =
                new ArtifactRetentionPolicyFieldCriteriaModel<>();
        pathsInclusionModel.setValues(List.of("in-path-1", "in-path-2"));
        paths.setInclude(pathsInclusionModel);
        searchCriteriaModel.setPaths(paths);

        // set names
        ArtifactRetentionPolicyInclusionCriteriaModel<String> namesInclusionModel =
                new ArtifactRetentionPolicyInclusionCriteriaModel<>();
        namesInclusionModel.setOperator("and");

        ArtifactRetentionPolicyFieldCriteriaModel<String> names =
                new ArtifactRetentionPolicyFieldCriteriaModel<>();
        namesInclusionModel.setValues(List.of("in-path-1", "in-path-2"));
        names.setInclude(namesInclusionModel);
        searchCriteriaModel.setNames(names);

        ArtifactRetentionPolicySearchCriteriaDescriptor searchCriteriaDescriptor =
                mapper.searchCriteriaModelToDescriptor(searchCriteriaModel);

        assertEquals(searchCriteriaModel.getCreatedBefore(), searchCriteriaDescriptor.getCreatedBefore());
        assertEquals(searchCriteriaModel.getDownloadedBefore(), searchCriteriaDescriptor.getDownloadedBefore());

        assertEquals(searchCriteriaModel.getProperties().getInclude().getOperator(),
                searchCriteriaDescriptor.getProperties().getInclude().getOperator());

        assertEquals(searchCriteriaModel.getProperties().getInclude().getValues().get(0).getPropertyKey(),
                searchCriteriaDescriptor.getProperties().getInclude().getValues().get(0)
                        .getPropertyKey());

        assertEquals(searchCriteriaModel.getProperties().getInclude().getValues().get(0).getPropertyValue(),
                searchCriteriaDescriptor.getProperties().getInclude().getValues().get(0).getPropertyValue());

        assertEquals(searchCriteriaModel.getRepositories().getInclude().getOperator(),
                searchCriteriaDescriptor.getRepositories().getInclude().getOperator());
        assertEquals(searchCriteriaModel.getRepositories().getInclude().getValues(),
                searchCriteriaDescriptor.getRepositories().getInclude().getValues());

        assertEquals(searchCriteriaModel.getPaths().getInclude().getOperator(),
                searchCriteriaDescriptor.getPaths().getInclude().getOperator());
        assertEquals(searchCriteriaModel.getPaths().getInclude().getValues(),
                searchCriteriaDescriptor.getPaths().getInclude().getValues());

        assertEquals(searchCriteriaModel.getNames().getInclude().getOperator(),
                searchCriteriaDescriptor.getNames().getInclude().getOperator());
        assertEquals(searchCriteriaModel.getNames().getInclude().getValues(),
                searchCriteriaDescriptor.getNames().getInclude().getValues());
    }

    @Test
    public void givenSearchCriteriaDescriptorToModel_whenMaps_thenCorrect() {
        ArtifactRetentionPolicySearchCriteriaDescriptor searchCriteriaDescriptor =
                new ArtifactRetentionPolicySearchCriteriaDescriptor();
        // set created before
        searchCriteriaDescriptor.setCreatedBefore(4L);
        // set downloaded before
        searchCriteriaDescriptor.setDownloadedBefore(5L);

        // set properties
        ArtifactRetentionPolicyPropertiesCriteriaDescriptor properties =
                new ArtifactRetentionPolicyPropertiesCriteriaDescriptor();
        ArtifactRetentionPolicyPropertyCriteriaDescriptor propertyDescriptor =
                new ArtifactRetentionPolicyPropertyCriteriaDescriptor();
        propertyDescriptor.setPropertyKey("propKey");
        propertyDescriptor.setPropertyValue("propValue");

        ArtifactRetentionPolicyPropertiesInclusionCriteriaDescriptor propertiesInclusion =
                new ArtifactRetentionPolicyPropertiesInclusionCriteriaDescriptor();
        propertiesInclusion.setOperator("or");
        propertiesInclusion.setValues(List.of(propertyDescriptor));

        properties.setInclude(propertiesInclusion);
        searchCriteriaDescriptor.setProperties(properties);

        // set repos
        ArtifactRetentionPolicyPathsCriteriaDescriptor repos =
                new ArtifactRetentionPolicyPathsCriteriaDescriptor();
        ArtifactRetentionPolicyPathsInclusionCriteriaDescriptor reposInclusion =
                new ArtifactRetentionPolicyPathsInclusionCriteriaDescriptor();
        reposInclusion.setOperator("and");
        reposInclusion.setValues(List.of("in-repo-1", "in-repo-2"));
        repos.setInclude(reposInclusion);
        searchCriteriaDescriptor.setRepositories(repos);

        // set paths
        ArtifactRetentionPolicyPathsCriteriaDescriptor paths =
                new ArtifactRetentionPolicyPathsCriteriaDescriptor();
        ArtifactRetentionPolicyPathsInclusionCriteriaDescriptor pathsInclusion =
                new ArtifactRetentionPolicyPathsInclusionCriteriaDescriptor();
        pathsInclusion.setOperator("or");
        pathsInclusion.setValues(List.of("in-path-1", "in-path-2"));
        paths.setInclude(pathsInclusion);
        searchCriteriaDescriptor.setPaths(paths);

        // set names
        ArtifactRetentionPolicyPathsCriteriaDescriptor names =
                new ArtifactRetentionPolicyPathsCriteriaDescriptor();
        ArtifactRetentionPolicyPathsInclusionCriteriaDescriptor namesInclusion =
                new ArtifactRetentionPolicyPathsInclusionCriteriaDescriptor();
        namesInclusion.setOperator("or");
        namesInclusion.setValues(List.of("in-name-1", "in-name-2"));
        names.setInclude(namesInclusion);
        searchCriteriaDescriptor.setNames(names);

        ArtifactRetentionCandidateCriteria searchCriteriaModel =
                mapper.searchCriteriaDescriptorToModel(searchCriteriaDescriptor);

        assertEquals(searchCriteriaDescriptor.getCreatedBefore(), searchCriteriaModel.getCreatedBefore());
        assertEquals(searchCriteriaModel.getDownloadedBefore(), searchCriteriaModel.getDownloadedBefore());

        assertEquals(searchCriteriaDescriptor.getProperties().getInclude().getOperator(),
                searchCriteriaModel.getProperties().getInclude().getOperator());

        assertEquals(
                searchCriteriaDescriptor.getProperties().getInclude().getValues().get(0).getPropertyKey(),
                searchCriteriaModel.getProperties().getInclude().getValues().get(0).getPropertyKey());

        assertEquals(
                searchCriteriaDescriptor.getProperties().getInclude().getValues().get(0)
                        .getPropertyValue(),
                searchCriteriaModel.getProperties().getInclude().getValues().get(0).getPropertyValue());

        assertEquals(searchCriteriaDescriptor.getRepositories().getInclude().getOperator(),
                searchCriteriaModel.getRepositories().getInclude().getOperator());
        assertEquals(searchCriteriaDescriptor.getRepositories().getInclude().getValues(),
                searchCriteriaModel.getRepositories().getInclude().getValues());

        assertEquals(searchCriteriaDescriptor.getPaths().getInclude().getOperator(),
                searchCriteriaModel.getPaths().getInclude().getOperator());
        assertEquals(searchCriteriaDescriptor.getPaths().getInclude().getValues(),
                searchCriteriaModel.getPaths().getInclude().getValues());

        assertEquals(searchCriteriaDescriptor.getNames().getInclude().getOperator(),
                searchCriteriaModel.getNames().getInclude().getOperator());
        assertEquals(searchCriteriaDescriptor.getNames().getInclude().getValues(),
                searchCriteriaModel.getNames().getInclude().getValues());
    }
}