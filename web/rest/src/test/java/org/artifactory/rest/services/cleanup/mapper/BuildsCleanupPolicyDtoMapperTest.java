package org.artifactory.rest.services.cleanup.mapper;

import org.artifactory.policy.PolicyItemType;
import org.artifactory.policy.PolicyType;
import org.artifactory.policy.cleanup.builds.BuildCleanupPolicy;
import org.artifactory.policy.cleanup.builds.BuildCleanupPolicyConfig;
import org.artifactory.policy.cleanup.builds.BuildCleanupPolicySearchCriteria;
import org.artifactory.rest.resource.cleanup.dto.BuildsCleanupPolicyDto;
import org.artifactory.rest.resource.cleanup.dto.BuildsCleanupPolicySearchCriteriaDto;
import org.jfrog.common.JsonUtils;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import java.util.List;

class BuildsCleanupPolicyDtoMapperTest {

    @Test
    void toDto() throws JSONException {
        BuildCleanupPolicy buildsCleanupPolicy = BuildCleanupPolicy.builder()
                .key("key")
                .type(PolicyType.CLEANUP)
                .itemType(PolicyItemType.BUILD)
                .created(1L)
                .createdBy("test")
                .modified(1L)
                .modifiedBy("test2")
                .cronExp("1 1 * * * ?")
                .enabled(false)
                .description("desc")
                .config(BuildCleanupPolicyConfig.builder()
                        .durationInMinutes(1)
                        .skipTrashcan(true)
                        .deleteArtifacts(true)
                        .searchCriteria(BuildCleanupPolicySearchCriteria.builder()
                                .includedBuilds(List.of(BuildCleanupPolicySearchCriteria.Build.builder()
                                        .projectKey("projectKey").name("name").build()))
                                .createdBeforeInMonths(1)
                                .build())
                        .build())
                .build();

        BuildsCleanupPolicyDto dto = BuildsCleanupPolicyDtoMapper.toDto(buildsCleanupPolicy);
        JSONAssert.assertEquals("""
                                {
                                  "key": "key",
                                  "itemType": "build",
                                  "description": "desc",
                                  "cronExp": "1 1 * * * ?",
                                  "durationInMinutes": 1,
                                  "enabled": false,
                                  "deleteArtifacts": true,
                                  "skipTrashcan": true,
                                  "searchCriteria": {
                                    "createdBeforeInMonths": 1,
                                    "includedBuilds": [
                                        {
                                          "projectKey": "projectKey",
                                          "name": "name"
                                        }
                                    ]
                                  }
                                }
                """, JsonUtils.getInstance().writeValueAsString(dto), JSONCompareMode.STRICT);
    }

    @Test
    void fromDto() throws JSONException {
        BuildsCleanupPolicyDto dto = BuildsCleanupPolicyDto.builder()
                .key("key")
                .searchCriteria(BuildsCleanupPolicySearchCriteriaDto
                        .builder()
                        .createdBeforeInMonths(1)
                        .includedBuilds(List.of(BuildsCleanupPolicySearchCriteriaDto.Builds.builder()
                                .projectKey("projectKey").name("name").build()))
                        .build())
                .skipTrashcan(true)
                .deleteArtifacts(true)
                .enabled(true)
                .cronExp("1 1 * * * ?")
                .description("desc")
                .durationInMinutes(1)
                .build();

        BuildCleanupPolicy buildsCleanupPolicy = BuildsCleanupPolicyDtoMapper.fromDto(dto);

        JSONAssert.assertEquals("""
                        {
                          "key": "key",
                          "type": "CLEANUP",
                          "itemType": "BUILD",
                          "enabled": true,
                          "created": 0,
                          "cronExp": "1 1 * * * ?",
                          "description": "desc",
                          "config": {
                            "skipTrashcan": true,
                            "deleteArtifacts": true,
                            "durationInMinutes": 1,
                            "searchCriteria": {
                              "createdBeforeInMonths": 1,
                              "timeBasedConditions": [1],
                              "includedBuilds": [
                                {
                                  "projectKey": "projectKey",
                                  "name": "name"
                                }
                              ]
                            }
                          }
                        }
                        """, JsonUtils.getInstance().writeValueAsString(buildsCleanupPolicy),
                JSONCompareMode.STRICT);
    }

    /** UI - This unit test case covers when a DTO received from frontend */
    @Test
    void fromDtoWithOnlyDays() throws JSONException {
        BuildsCleanupPolicyDto dto = BuildsCleanupPolicyDto.builder()
                .key("key")
                .searchCriteria(BuildsCleanupPolicySearchCriteriaDto
                        .builder()
                        .createdBeforeInDays(2)
                        .includedBuilds(List.of(BuildsCleanupPolicySearchCriteriaDto.Builds.builder()
                                .projectKey("projectKey").name("name").build()))
                        .build())
                .skipTrashcan(true)
                .deleteArtifacts(true)
                .enabled(true)
                .cronExp("1 1 * * * ?")
                .description("desc")
                .durationInMinutes(1)
                .build();

        BuildCleanupPolicy buildsCleanupPolicy = BuildsCleanupPolicyDtoMapper.fromDto(dto);

        JSONAssert.assertEquals("""
                        {
                          "key": "key",
                          "type": "CLEANUP",
                          "itemType": "BUILD",
                          "enabled": true,
                          "created": 0,
                          "cronExp": "1 1 * * * ?",
                          "description": "desc",
                          "config": {
                            "skipTrashcan": true,
                            "deleteArtifacts": true,
                            "durationInMinutes": 1,
                            "searchCriteria": {
                              "createdBeforeInDays": 2,
                              "timeBasedConditions": [2],
                              "includedBuilds": [
                                {
                                  "projectKey": "projectKey",
                                  "name": "name"
                                }
                              ]
                            }
                          }
                        }
                        """, JsonUtils.getInstance().writeValueAsString(buildsCleanupPolicy),
                JSONCompareMode.STRICT);
    }

    /** Passed from API DTO should have both time base value as this is required while validation */
    @Test
    void fromDtoWithBothMonthsAndDays() throws JSONException {
        BuildsCleanupPolicyDto dto = BuildsCleanupPolicyDto.builder()
                .key("key")
                .searchCriteria(BuildsCleanupPolicySearchCriteriaDto
                        .builder()
                        .createdBeforeInMonths(1)
                        .createdBeforeInDays(30)
                        .includedBuilds(List.of(BuildsCleanupPolicySearchCriteriaDto.Builds.builder()
                                .projectKey("projectKey").name("name").build()))
                        .build())
                .skipTrashcan(true)
                .deleteArtifacts(true)
                .enabled(true)
                .cronExp("1 1 * * * ?")
                .description("desc")
                .durationInMinutes(1)
                .build();

        BuildCleanupPolicy buildsCleanupPolicy = BuildsCleanupPolicyDtoMapper.fromDto(dto);

        JSONAssert.assertEquals("""
                        {
                          "key": "key",
                          "type": "CLEANUP",
                          "itemType": "BUILD",
                          "enabled": true,
                          "created": 0,
                          "cronExp": "1 1 * * * ?",
                          "description": "desc",
                          "config": {
                            "skipTrashcan": true,
                            "deleteArtifacts": true,
                            "durationInMinutes": 1,
                            "searchCriteria": {
                              "createdBeforeInMonths": 1,
                              "createdBeforeInDays": 30,
                              "timeBasedConditions": [1, 30],
                              "includedBuilds": [
                                {
                                  "projectKey": "projectKey",
                                  "name": "name"
                                }
                              ]
                            }
                          }
                        }
                        """, JsonUtils.getInstance().writeValueAsString(buildsCleanupPolicy),
                JSONCompareMode.STRICT);
    }

    /** New Policy after days change will always have months and days but not individuals */
    @Test
    void toDtoWithBothMonthsAndDays() throws JSONException {
        BuildCleanupPolicy buildsCleanupPolicy = BuildCleanupPolicy.builder()
                .key("key")
                .type(PolicyType.CLEANUP)
                .itemType(PolicyItemType.BUILD)
                .created(1L)
                .createdBy("test")
                .modified(1L)
                .modifiedBy("test2")
                .cronExp("1 1 * * * ?")
                .enabled(false)
                .description("desc")
                .config(BuildCleanupPolicyConfig.builder()
                        .durationInMinutes(1)
                        .skipTrashcan(true)
                        .deleteArtifacts(true)
                        .searchCriteria(BuildCleanupPolicySearchCriteria.builder()
                                .includedBuilds(List.of(BuildCleanupPolicySearchCriteria.Build.builder()
                                        .projectKey("projectKey").name("name").build()))
                                .createdBeforeInMonths(1)
                                .createdBeforeInDays(30)
                                .build())
                        .build())
                .build();

        BuildsCleanupPolicyDto dto = BuildsCleanupPolicyDtoMapper.toDto(buildsCleanupPolicy);
        JSONAssert.assertEquals("""
                                {
                                  "key": "key",
                                  "itemType": "build",
                                  "description": "desc",
                                  "cronExp": "1 1 * * * ?",
                                  "durationInMinutes": 1,
                                  "enabled": false,
                                  "deleteArtifacts": true,
                                  "skipTrashcan": true,
                                  "searchCriteria": {
                                    "createdBeforeInMonths": 1,
                                    "createdBeforeInDays": 30,
                                    "includedBuilds": [
                                        {
                                          "projectKey": "projectKey",
                                          "name": "name"
                                        }
                                    ]
                                  }
                                }
                """, JsonUtils.getInstance().writeValueAsString(dto), JSONCompareMode.STRICT);
    }

    /** Set default time based condition */
    @Test
    void toBaseDtoWhenNoTimeBasedConditionPassed() throws JSONException {
        BuildsCleanupPolicyDto dto = BuildsCleanupPolicyDto.builder()
                .key("key")
                .searchCriteria(BuildsCleanupPolicySearchCriteriaDto
                        .builder()
                        .includedBuilds(List.of(BuildsCleanupPolicySearchCriteriaDto.Builds.builder()
                                .projectKey("projectKey").name("name").build()))
                        .build())
                .skipTrashcan(true)
                .deleteArtifacts(true)
                .enabled(true)
                .cronExp("1 1 * * * ?")
                .description("desc")
                .durationInMinutes(1)
                .build();

        BuildCleanupPolicy buildsCleanupPolicy = BuildsCleanupPolicyDtoMapper.fromDto(dto);

        JSONAssert.assertEquals("""
                        {
                          "key": "key",
                          "type": "CLEANUP",
                          "itemType": "BUILD",
                          "enabled": true,
                          "created": 0,
                          "cronExp": "1 1 * * * ?",
                          "description": "desc",
                          "config": {
                            "skipTrashcan": true,
                            "deleteArtifacts": true,
                            "durationInMinutes": 1,
                            "searchCriteria": {
                              "createdBeforeInDays": 730,
                              "timeBasedConditions": [730],
                              "includedBuilds": [
                                {
                                  "projectKey": "projectKey",
                                  "name": "name"
                                }
                              ]
                            }
                          }
                        }
                        """, JsonUtils.getInstance().writeValueAsString(buildsCleanupPolicy),
                JSONCompareMode.STRICT);
    }
}