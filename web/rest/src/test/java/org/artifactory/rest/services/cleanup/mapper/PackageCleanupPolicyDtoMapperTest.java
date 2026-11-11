package org.artifactory.rest.services.cleanup.mapper;

import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.policy.PackagePolicySearchCriteria;
import org.artifactory.policy.PolicyItemType;
import org.artifactory.policy.PolicyType;
import org.artifactory.policy.RepoAvailabilityStatus;
import org.artifactory.policy.cleanup.packages.PackageCleanupPolicy;
import org.artifactory.policy.cleanup.packages.PackageCleanupPolicyConfig;
import org.artifactory.rest.resource.cleanup.dto.PackageCleanupPolicyDto;
import org.artifactory.rest.resource.cleanup.dto.PackageCleanupPolicySearchCriteriaDto;
import org.artifactory.test.ArtifactoryHomeStub;
import org.jfrog.common.JsonUtils;
import org.json.JSONException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import java.util.List;
import java.util.Map;
import java.util.Set;

class PackageCleanupPolicyDtoMapperTest {

    @BeforeEach
    void setUp() {
        ArtifactoryHomeStub home = new ArtifactoryHomeStub(getClass().getName());
        home.loadSystemProperties();
        ArtifactoryHome.bind(home);
    }

    @AfterEach
    void tearDown() {
        // Unbind ArtifactoryHome from the current thread
        ArtifactoryHome.unbind();
    }
    @Test
    void toDto() throws JSONException {
        PackageCleanupPolicy packageCleanupPolicy = PackageCleanupPolicy.builder()
                .key("key")
                .type(PolicyType.CLEANUP)
                .itemType(PolicyItemType.PACKAGE)
                .created(1L)
                .createdBy("test")
                .modified(1L)
                .modifiedBy("test2")
                .cronExp("1 1 * * * ?")
                .enabled(true)
                .description("desc")
                .config(PackageCleanupPolicyConfig.builder()
                        .skipTrashcan(true)
                        .durationInMinutes(1)
                        .searchCriteria(PackagePolicySearchCriteria.builder()
                                .repos(List.of("repo1", "repo2"))
                                .excludedRepos(List.of("ex-repo3", "ex-repo4"))
                                .includedPackages(List.of("package1"))
                                .excludedPackages(List.of("ex-package2", "ex-package3"))
                                .lastDownloadedBeforeInMonths(1)
                                .createdBeforeInMonths(1)
                                .build())
                        .build())
                .build();

        PackageCleanupPolicyDto dto = PackageCleanupPolicyDtoMapper.toDto(packageCleanupPolicy);
        JSONAssert.assertEquals("""
                                {
                                  "key": "key",
                                  "description": "desc",
                                  "cronExp": "1 1 * * * ?",
                                  "durationInMinutes": 1,
                                  "itemType": "package",
                                  "enabled": true,
                                  "skipTrashcan": true,
                                  "searchCriteria": {
                                    "repos": [
                                      "repo1",
                                      "repo2"
                                    ],
                                    "excludedRepos": [
                                      "ex-repo3",
                                      "ex-repo4"
                                    ],
                                    "includedPackages": [
                                      "package1"
                                    ],
                                    "excludedPackages": [
                                      "ex-package2",
                                      "ex-package3"
                                    ],
                                    "includedPackageVersions": ["**"],
                                    "excludedPackageVersions": [],
                                    "includeAllStages": true,
                                    "createdBeforeInMonths": 1,
                                    "lastDownloadedBeforeInMonths": 1
                                  }
                                }
                """, JsonUtils.getInstance().writeValueAsString(dto), JSONCompareMode.STRICT);
    }

    @Test
    void fromDtoIfMonthsAndDaysBothArePassed() throws JSONException {
        PackageCleanupPolicyDto dto = PackageCleanupPolicyDto.builder()
                .key("key")
                .searchCriteria(PackageCleanupPolicySearchCriteriaDto
                        .builder()
                        .repos(List.of("repo1", "repo2"))
                        .excludedRepos(List.of("ex-repo3", "ex-repo4"))
                        .includedPackages(List.of("package1"))
                        .excludedPackages(List.of("ex-package2", "ex-package3"))
                        .createdBeforeInDays(3)
                        .lastDownloadedBeforeInDays(2)
                        .lastDownloadedBeforeInMonths(1)
                        .createdBeforeInMonths(1)
                        .build())
                .enabled(true)
                .cronExp("1 1 * * * ?")
                .description("desc")
                .durationInMinutes(1)
                .skipTrashcan(true)
                .build();

        PackageCleanupPolicy packageCleanupPolicy = PackageCleanupPolicyDtoMapper.fromDto(dto);

        JSONAssert.assertEquals("""
                        {
                          "key": "key",
                          "type": "CLEANUP",
                          "itemType": "PACKAGE",
                          "enabled": true,
                          "created": 0,
                          "cronExp": "1 1 * * * ?",
                          "description": "desc",
                          "config": {
                            "skipTrashcan": true,
                            "durationInMinutes": 1,
                            "searchCriteria": {
                              "repos": [
                                "repo1",
                                "repo2"
                              ],
                              "excludedRepos": [
                                "ex-repo3",
                                "ex-repo4"
                              ],
                               "includedPackages": [
                                 "package1"
                                ],
                                "excludedPackages": [
                                  "ex-package2",
                                  "ex-package3"
                                 ],
                              "includedPackageVersions": ["**"],
                              "excludedPackageVersions": [],
                              "createdBeforeInDays": 3,
                              "lastDownloadedBeforeInDays": 2,
                              "createdBeforeInMonths": 1,
                              "lastDownloadedBeforeInMonths": 1,
                              "timeBasedConditions":[1,1,3,2],
                              "versionsBasedConditions":[],
                              "includeAllStages": true
                            }
                          }
                        }
                        """, JsonUtils.getInstance().writeValueAsString(packageCleanupPolicy),
                JSONCompareMode.STRICT);
    }

    @Test
    void fromDtoIfOnlyDaysArePassed() throws JSONException {
        PackageCleanupPolicyDto dto = PackageCleanupPolicyDto.builder()
                .key("key")
                .searchCriteria(PackageCleanupPolicySearchCriteriaDto
                        .builder()
                        .repos(List.of("repo1", "repo2"))
                        .excludedRepos(List.of("ex-repo3", "ex-repo4"))
                        .includedPackages(List.of("package1"))
                        .excludedPackages(List.of("ex-package2", "ex-package3"))
                        .createdBeforeInDays(3)
                        .lastDownloadedBeforeInDays(2)
                        .build())
                .enabled(true)
                .cronExp("1 1 * * * ?")
                .description("desc")
                .durationInMinutes(1)
                .skipTrashcan(true)
                .build();

        PackageCleanupPolicy packageCleanupPolicy = PackageCleanupPolicyDtoMapper.fromDto(dto);

        JSONAssert.assertEquals("""
                        {
                          "key": "key",
                          "type": "CLEANUP",
                          "itemType": "PACKAGE",
                          "enabled": true,
                          "created": 0,
                          "cronExp": "1 1 * * * ?",
                          "description": "desc",
                          "config": {
                            "skipTrashcan": true,
                            "durationInMinutes": 1,
                            "searchCriteria": {
                              "repos": [
                                "repo1",
                                "repo2"
                              ],
                              "excludedRepos": [
                                "ex-repo3",
                                "ex-repo4"
                              ],
                               "includedPackages": [
                                 "package1"
                                ],
                                "excludedPackages": [
                                  "ex-package2",
                                  "ex-package3"
                                 ],
                              "includedPackageVersions": ["**"],
                              "excludedPackageVersions": [],
                              "includeAllStages": true,
                              "createdBeforeInDays": 3,
                              "lastDownloadedBeforeInDays": 2,
                              "timeBasedConditions":[3,2],
                              "versionsBasedConditions":[]
                            }
                          }
                        }
                        """, JsonUtils.getInstance().writeValueAsString(packageCleanupPolicy),
                JSONCompareMode.STRICT);
    }

    @Test
    void fromDtoIfNoConditionsArePassed() throws JSONException {
        PackageCleanupPolicyDto dto = PackageCleanupPolicyDto.builder()
                .key("key")
                .searchCriteria(PackageCleanupPolicySearchCriteriaDto
                        .builder()
                        .repos(List.of("repo1", "repo2"))
                        .excludedRepos(List.of("ex-repo3", "ex-repo4"))
                        .includedPackages(List.of("package1"))
                        .excludedPackages(List.of("ex-package2", "ex-package3"))
                        .build())
                .enabled(true)
                .cronExp("1 1 * * * ?")
                .description("desc")
                .durationInMinutes(1)
                .skipTrashcan(true)
                .build();

        PackageCleanupPolicy packageCleanupPolicy = PackageCleanupPolicyDtoMapper.fromDto(dto);

        JSONAssert.assertEquals("""
                        {
                          "key": "key",
                          "type": "CLEANUP",
                          "itemType": "PACKAGE",
                          "enabled": true,
                          "created": 0,
                          "cronExp": "1 1 * * * ?",
                          "description": "desc",
                          "config": {
                            "skipTrashcan": true,
                            "durationInMinutes": 1,
                            "searchCriteria": {
                              "repos": [
                                "repo1",
                                "repo2"
                              ],
                              "excludedRepos": [
                                "ex-repo3",
                                "ex-repo4"
                              ],
                               "includedPackages": [
                                 "package1"
                                ],
                                "excludedPackages": [
                                  "ex-package2",
                                  "ex-package3"
                                 ],
                              "includedPackageVersions": ["**"],
                              "excludedPackageVersions": [],
                              "includeAllStages": true,
                              "createdBeforeInDays": 730,
                              "lastDownloadedBeforeInDays": 730,
                              "keepLastNVersions": 0,
                              "timeBasedConditions":[730,730],
                              "versionsBasedConditions":[0]
                            }
                          }
                        }
                        """, JsonUtils.getInstance().writeValueAsString(packageCleanupPolicy),
                JSONCompareMode.STRICT);
    }

    @Test
    void fromDto() throws JSONException {
        PackageCleanupPolicyDto dto = PackageCleanupPolicyDto.builder()
                .key("key")
                .searchCriteria(PackageCleanupPolicySearchCriteriaDto
                        .builder()
                        .repos(List.of("repo1", "repo2"))
                        .excludedRepos(List.of("ex-repo3", "ex-repo4"))
                        .includedPackages(List.of("package1"))
                        .excludedPackages(List.of("ex-package2", "ex-package3"))
                        .lastDownloadedBeforeInMonths(1)
                        .createdBeforeInMonths(1)
                        .build())
                .enabled(true)
                .cronExp("1 1 * * * ?")
                .description("desc")
                .durationInMinutes(1)
                .skipTrashcan(true)
                .build();

        PackageCleanupPolicy packageCleanupPolicy = PackageCleanupPolicyDtoMapper.fromDto(dto);

        JSONAssert.assertEquals("""
                        {
                          "key": "key",
                          "type": "CLEANUP",
                          "itemType": "PACKAGE",
                          "enabled": true,
                          "created": 0,
                          "cronExp": "1 1 * * * ?",
                          "description": "desc",
                          "config": {
                            "skipTrashcan": true,
                            "durationInMinutes": 1,
                            "searchCriteria": {
                              "repos": [
                                "repo1",
                                "repo2"
                              ],
                              "excludedRepos": [
                                "ex-repo3",
                                "ex-repo4"
                              ],
                               "includedPackages": [
                                 "package1"
                                ],
                                "excludedPackages": [
                                  "ex-package2",
                                  "ex-package3"
                                 ],
                              "includedPackageVersions": ["**"],
                              "excludedPackageVersions": [],
                              "createdBeforeInMonths": 1,
                              "lastDownloadedBeforeInMonths": 1,
                              "timeBasedConditions":[1,1],
                              "versionsBasedConditions":[],
                              "includeAllStages": true
                            }
                          }
                        }
                        """, JsonUtils.getInstance().writeValueAsString(packageCleanupPolicy),
                JSONCompareMode.STRICT);
    }

    @Test
    void toViewDto_withReposWithStatus() throws JSONException {
        PackageCleanupPolicy packageCleanupPolicy = PackageCleanupPolicy.builder()
                .key("key")
                .type(PolicyType.CLEANUP)
                .itemType(PolicyItemType.PACKAGE)
                .created(1L)
                .createdBy("test")
                .modified(1L)
                .modifiedBy("test2")
                .cronExp("1 1 * * * ?")
                .enabled(true)
                .description("desc")
                .config(PackageCleanupPolicyConfig.builder()
                        .skipTrashcan(true)
                        .durationInMinutes(1)
                        .searchCriteria(PackagePolicySearchCriteria.builder()
                                .repos(List.of("repo1", "repo2"))
                                .excludedRepos(List.of("ex-repo3", "ex-repo4"))
                                .includedPackages(List.of("package1"))
                                .excludedPackages(List.of("ex-package2", "ex-package3"))
                                .lastDownloadedBeforeInMonths(1)
                                .createdBeforeInMonths(1)
                                .reposWithStatus(List.of(
                                        new RepoAvailabilityStatus("repo", false),
                                        new RepoAvailabilityStatus("repo2", true)))
                                .build())
                        .build())
                .build();

        PackageCleanupPolicyDto dto = PackageCleanupPolicyDtoMapper.toViewDto(packageCleanupPolicy);

        JSONAssert.assertEquals("""
                {
                  "key": "key",
                  "description": "desc",
                  "cronExp": "1 1 * * * ?",
                  "durationInMinutes": 1,
                  "itemType": "package",
                  "enabled": true,
                  "skipTrashcan": true,
                  "searchCriteria": {
                    "repos": ["repo1", "repo2"],
                    "excludedRepos": ["ex-repo3", "ex-repo4"],
                    "includedPackages": ["package1"],
                    "excludedPackages": ["ex-package2", "ex-package3"],
                    "includedPackageVersions": ["**"],
                    "excludedPackageVersions": [],
                    "includeAllStages": true,
                    "createdBeforeInMonths": 1,
                    "lastDownloadedBeforeInMonths": 1,
                    "reposWithStatus": [
                      {"repoName": "repo", "isAvailable": false},
                      {"repoName": "repo2", "isAvailable": true}
                    ]
                  }
                }
                """, JsonUtils.getInstance().writeValueAsString(dto), JSONCompareMode.STRICT);
    }


    @Test
    void toViewDto_withEmptyReposWithStatus() throws JSONException {
        PackageCleanupPolicy packageCleanupPolicy = PackageCleanupPolicy.builder()
                .key("key")
                .type(PolicyType.CLEANUP)
                .itemType(PolicyItemType.PACKAGE)
                .created(1L)
                .createdBy("test")
                .modified(1L)
                .modifiedBy("test2")
                .cronExp("1 1 * * * ?")
                .enabled(true)
                .description("desc")
                .config(PackageCleanupPolicyConfig.builder()
                        .skipTrashcan(true)
                        .durationInMinutes(1)
                        .searchCriteria(PackagePolicySearchCriteria.builder()
                                .repos(List.of("repo1", "repo2"))
                                .excludedRepos(List.of("ex-repo3", "ex-repo4"))
                                .includedPackages(List.of("package1"))
                                .excludedPackages(List.of("ex-package2", "ex-package3"))
                                .lastDownloadedBeforeInMonths(1)
                                .createdBeforeInMonths(1)
                                .reposWithStatus(List.of())
                                .build())
                        .build())
                .build();

        PackageCleanupPolicyDto dto = PackageCleanupPolicyDtoMapper.toViewDto(packageCleanupPolicy);

        JSONAssert.assertEquals("""
                {
                  "key": "key",
                  "description": "desc",
                  "cronExp": "1 1 * * * ?",
                  "durationInMinutes": 1,
                  "itemType": "package",
                  "enabled": true,
                  "skipTrashcan": true,
                  "searchCriteria": {
                    "repos": ["repo1", "repo2"],
                    "excludedRepos": ["ex-repo3", "ex-repo4"],
                    "includedPackages": ["package1"],
                    "excludedPackages": ["ex-package2", "ex-package3"],
                    "includedPackageVersions": ["**"],
                    "excludedPackageVersions": [],
                    "includeAllStages": true,
                    "createdBeforeInMonths": 1,
                    "lastDownloadedBeforeInMonths": 1,
                    "reposWithStatus": []
                  }
                }
                """, JsonUtils.getInstance().writeValueAsString(dto), JSONCompareMode.STRICT);
    }

    @Test
    void toViewDto_withoutReposWithStatus() throws JSONException {
        PackageCleanupPolicy packageCleanupPolicy = PackageCleanupPolicy.builder()
                .key("key")
                .type(PolicyType.CLEANUP)
                .itemType(PolicyItemType.PACKAGE)
                .created(1L)
                .createdBy("test")
                .modified(1L)
                .modifiedBy("test2")
                .cronExp("1 1 * * * ?")
                .enabled(true)
                .description("desc")
                .config(PackageCleanupPolicyConfig.builder()
                        .skipTrashcan(true)
                        .durationInMinutes(1)
                        .searchCriteria(PackagePolicySearchCriteria.builder()
                                .repos(List.of("repo1", "repo2"))
                                .excludedRepos(List.of("ex-repo3", "ex-repo4"))
                                .includedPackages(List.of("package1"))
                                .excludedPackages(List.of("ex-package2", "ex-package3"))
                                .lastDownloadedBeforeInMonths(1)
                                .createdBeforeInMonths(1)
                                .build())
                        .build())
                .build();

        PackageCleanupPolicyDto dto = PackageCleanupPolicyDtoMapper.toViewDto(packageCleanupPolicy);

        JSONAssert.assertEquals("""
                {
                  "key": "key",
                  "description": "desc",
                  "cronExp": "1 1 * * * ?",
                  "durationInMinutes": 1,
                  "itemType": "package",
                  "enabled": true,
                  "skipTrashcan": true,
                  "searchCriteria": {
                    "repos": ["repo1", "repo2"],
                    "excludedRepos": ["ex-repo3", "ex-repo4"],
                    "includedPackages": ["package1"],
                    "excludedPackages": ["ex-package2", "ex-package3"],
                    "includedPackageVersions": ["**"],
                    "excludedPackageVersions": [],
                    "includeAllStages": true,
                    "createdBeforeInMonths": 1,
                    "lastDownloadedBeforeInMonths": 1
                  }
                }
                """, JsonUtils.getInstance().writeValueAsString(dto), JSONCompareMode.STRICT);
    }

    @Test
    void toDto_WithPropertyBasedConditions() throws JSONException {
        PackageCleanupPolicy packageCleanupPolicy = PackageCleanupPolicy.builder()
                .key("key")
                .type(PolicyType.CLEANUP)
                .itemType(PolicyItemType.PACKAGE)
                .created(1L)
                .createdBy("test")
                .modified(1L)
                .modifiedBy("test2")
                .cronExp("1 1 * * * ?")
                .enabled(true)
                .description("desc")
                .config(PackageCleanupPolicyConfig.builder()
                        .skipTrashcan(true)
                        .durationInMinutes(1)
                        .searchCriteria(PackagePolicySearchCriteria.builder()
                                .repos(List.of("repo1", "repo2"))
                                .excludedRepos(List.of("ex-repo3", "ex-repo4"))
                                .includedPackages(List.of("package1"))
                                .excludedPackages(List.of("ex-package2", "ex-package3"))
                                .includedProperties(Map.of("environment", List.of("prod")))
                                .excludedProperties(Map.of("arch", List.of("x86")))
                                .build())
                        .build())
                .build();

        PackageCleanupPolicyDto dto = PackageCleanupPolicyDtoMapper.toDto(packageCleanupPolicy);

        JSONAssert.assertEquals("""
        {
          "key": "key",
          "description": "desc",
          "cronExp": "1 1 * * * ?",
          "durationInMinutes": 1,
          "itemType": "package",
          "enabled": true,
          "skipTrashcan": true,
          "searchCriteria": {
            "repos": ["repo1", "repo2"],
            "excludedRepos": ["ex-repo3", "ex-repo4"],
            "includedPackages": ["package1"],
            "excludedPackages": ["ex-package2", "ex-package3"],
            "includedPackageVersions": ["**"],
            "excludedPackageVersions": [],
            "includeAllStages": true,
            "includedProperties": {
              "environment": ["prod"]
            },
            "excludedProperties": {
              "arch": ["x86"]
            }
          }
        }""", JsonUtils.getInstance().writeValueAsString(dto), JSONCompareMode.STRICT);
    }

    @Test
    void toViewDto_withPolicyBasedOnlyOnDays() throws JSONException {
        PackageCleanupPolicy packageCleanupPolicy = PackageCleanupPolicy.builder()
                .key("key")
                .type(PolicyType.CLEANUP)
                .itemType(PolicyItemType.PACKAGE)
                .created(1L)
                .createdBy("test")
                .modified(1L)
                .modifiedBy("test2")
                .cronExp("1 1 * * * ?")
                .enabled(true)
                .description("desc")
                .config(PackageCleanupPolicyConfig.builder()
                        .skipTrashcan(true)
                        .durationInMinutes(1)
                        .searchCriteria(PackagePolicySearchCriteria.builder()
                                .repos(List.of("repo1", "repo2"))
                                .excludedRepos(List.of("ex-repo3", "ex-repo4"))
                                .includedPackages(List.of("package1"))
                                .excludedPackages(List.of("ex-package2", "ex-package3"))
                                .lastDownloadedBeforeInDays(1)
                                .createdBeforeInDays(1)
                                .build())
                        .build())
                .build();

        PackageCleanupPolicyDto dto = PackageCleanupPolicyDtoMapper.toViewDto(packageCleanupPolicy);

        JSONAssert.assertEquals("""
                {
                  "key": "key",
                  "description": "desc",
                  "cronExp": "1 1 * * * ?",
                  "durationInMinutes": 1,
                  "itemType": "package",
                  "enabled": true,
                  "skipTrashcan": true,
                  "searchCriteria": {
                    "repos": ["repo1", "repo2"],
                    "excludedRepos": ["ex-repo3", "ex-repo4"],
                    "includedPackages": ["package1"],
                    "excludedPackages": ["ex-package2", "ex-package3"],
                    "includedPackageVersions": ["**"],
                    "excludedPackageVersions": [],
                    "includeAllStages": true,
                    "createdBeforeInDays": 1,
                    "lastDownloadedBeforeInDays": 1
                  }
                }
                """, JsonUtils.getInstance().writeValueAsString(dto), JSONCompareMode.STRICT);
    }

    @Test
    void toCriteriaDto_withVersionPatternEnabled() throws JSONException {
        ArtifactoryHomeStub home = new ArtifactoryHomeStub(getClass().getName());
        home.loadSystemProperties();
        ArtifactoryHome.bind(home);
        
        PackagePolicySearchCriteria criteria = PackagePolicySearchCriteria.builder()
                .repos(List.of("repo1"))
                .includedPackageVersions(List.of("1.0.0", "2.0.0"))
                .excludedPackageVersions(List.of("3.0.0"))
                .build();

        PackageCleanupPolicySearchCriteriaDto dto = PackageCleanupPolicyDtoMapper.toCriteriaDto(criteria);

        JSONAssert.assertEquals("""
                {
                  "repos": ["repo1"],
                  "createdBeforeInDays": 730,
                  "lastDownloadedBeforeInDays": 730,
                  "keepLastNVersions": 0,
                  "includedPackageVersions": ["1.0.0", "2.0.0"],
                  "excludedPackageVersions": ["3.0.0"],
                  "includeAllStages": true
                }
                """, JsonUtils.getInstance().writeValueAsString(dto), JSONCompareMode.STRICT);
    }

    @Test
    void toCriteriaDto_withVersionPatternEnabledAndNullIncludedVersions() throws JSONException {
        ArtifactoryHomeStub home = new ArtifactoryHomeStub(getClass().getName());
        home.loadSystemProperties();
        ArtifactoryHome.bind(home);
        
        PackagePolicySearchCriteria criteria = PackagePolicySearchCriteria.builder()
                .repos(List.of("repo1"))
                .includedPackageVersions(null)
                .excludedPackageVersions(List.of("3.0.0"))
                .build();

        PackageCleanupPolicySearchCriteriaDto dto = PackageCleanupPolicyDtoMapper.toCriteriaDto(criteria);

        JSONAssert.assertEquals("""
                {
                  "repos": ["repo1"],
                  "createdBeforeInDays": 730,
                  "lastDownloadedBeforeInDays": 730,
                  "keepLastNVersions": 0,
                  "includedPackageVersions": ["**"],
                  "excludedPackageVersions": ["3.0.0"],
                  "includeAllStages": true
                }
                """, JsonUtils.getInstance().writeValueAsString(dto), JSONCompareMode.STRICT);
    }

    @Test
    void toCriteriaDto_withVersionPatternEnabledAndEmptyIncludedVersions() throws JSONException {
        ArtifactoryHomeStub home = new ArtifactoryHomeStub(getClass().getName());
        home.loadSystemProperties();
        ArtifactoryHome.bind(home);
        
        PackagePolicySearchCriteria criteria = PackagePolicySearchCriteria.builder()
                .repos(List.of("repo1"))
                .includedPackageVersions(List.of())
                .excludedPackageVersions(List.of("3.0.0"))
                .build();

        PackageCleanupPolicySearchCriteriaDto dto = PackageCleanupPolicyDtoMapper.toCriteriaDto(criteria);

        JSONAssert.assertEquals("""
                {
                  "repos": ["repo1"],
                  "createdBeforeInDays": 730,
                  "lastDownloadedBeforeInDays": 730,
                  "keepLastNVersions": 0,
                  "includedPackageVersions": ["**"],
                  "excludedPackageVersions": ["3.0.0"],
                  "includeAllStages": true
                }
                """, JsonUtils.getInstance().writeValueAsString(dto), JSONCompareMode.STRICT);
    }

    @Test
    void toCriteriaDto_withVersionPatternEnabledAndNullExcludedVersions() throws JSONException {
        ArtifactoryHomeStub home = new ArtifactoryHomeStub(getClass().getName());
        home.loadSystemProperties();
        ArtifactoryHome.bind(home);
        
        PackagePolicySearchCriteria criteria = PackagePolicySearchCriteria.builder()
                .repos(List.of("repo1"))
                .includedPackageVersions(List.of("1.0.0"))
                .excludedPackageVersions(null)
                .build();

        PackageCleanupPolicySearchCriteriaDto dto = PackageCleanupPolicyDtoMapper.toCriteriaDto(criteria);

        JSONAssert.assertEquals("""
                {
                  "repos": ["repo1"],
                  "createdBeforeInDays": 730,
                  "lastDownloadedBeforeInDays": 730,
                  "keepLastNVersions": 0,
                  "includedPackageVersions": ["1.0.0"],
                  "excludedPackageVersions": [],
                  "includeAllStages": true
                }
                """, JsonUtils.getInstance().writeValueAsString(dto), JSONCompareMode.STRICT);
    }

    @Test
    void toViewCriteriaDto_withVersionPatternEnabled() throws JSONException {
        ArtifactoryHomeStub home = new ArtifactoryHomeStub(getClass().getName());
        home.loadSystemProperties();
        ArtifactoryHome.bind(home);
        
        PackagePolicySearchCriteria criteria = PackagePolicySearchCriteria.builder()
                .repos(List.of("repo1"))
                .includedPackageVersions(List.of("1.0.0"))
                .excludedPackageVersions(List.of("2.0.0"))
                .reposWithStatus(List.of(new RepoAvailabilityStatus("repo1", true)))
                .build();

        PackageCleanupPolicySearchCriteriaDto dto = PackageCleanupPolicyDtoMapper.toViewCriteriaDto(criteria);

        JSONAssert.assertEquals("""
                {
                  "repos": ["repo1"],
                  "createdBeforeInDays": 730,
                  "lastDownloadedBeforeInDays": 730,
                  "keepLastNVersions": 0,
                  "includedPackageVersions": ["1.0.0"],
                  "excludedPackageVersions": ["2.0.0"],
                  "includeAllStages": true,
                  "reposWithStatus": [
                    {"repoName": "repo1", "isAvailable": true}
                  ]
                }
                """, JsonUtils.getInstance().writeValueAsString(dto), JSONCompareMode.STRICT);
    }

    @Test
    void fromCriteriaDto_withVersionPatternEnabled() throws JSONException {
        ArtifactoryHomeStub home = new ArtifactoryHomeStub(getClass().getName());
        home.loadSystemProperties();
        ArtifactoryHome.bind(home);
        
        PackageCleanupPolicySearchCriteriaDto criteriaDto = PackageCleanupPolicySearchCriteriaDto.builder()
                .repos(List.of("repo1"))
                .includedPackageVersions(List.of("1.0.0", "2.0.0"))
                .excludedPackageVersions(List.of("3.0.0"))
                .build();

        PackagePolicySearchCriteria criteria = PackageCleanupPolicyDtoMapper.fromCriteriaDto(criteriaDto);

        JSONAssert.assertEquals("""
                {
                  "repos": ["repo1"],
                  "createdBeforeInDays": 730,
                  "lastDownloadedBeforeInDays": 730,
                  "keepLastNVersions": 0,
                  "includedPackageVersions": ["1.0.0", "2.0.0"],
                  "excludedPackageVersions": ["3.0.0"],
                  "timeBasedConditions": [730, 730],
                  "includeAllStages": true,
                  "versionsBasedConditions": [0]
                }
                """, JsonUtils.getInstance().writeValueAsString(criteria), JSONCompareMode.STRICT);
    }

    @Test
    void fromCriteriaDto_withVersionPatternEnabledAndNullVersions() throws JSONException {
        ArtifactoryHomeStub home = new ArtifactoryHomeStub(getClass().getName());
        home.loadSystemProperties();
        ArtifactoryHome.bind(home);
        
        PackageCleanupPolicySearchCriteriaDto criteriaDto = PackageCleanupPolicySearchCriteriaDto.builder()
                .repos(List.of("repo1"))
                .includedPackageVersions(null)
                .excludedPackageVersions(null)
                .build();

        PackagePolicySearchCriteria criteria = PackageCleanupPolicyDtoMapper.fromCriteriaDto(criteriaDto);

        JSONAssert.assertEquals("""
                {
                  "repos": ["repo1"],
                  "createdBeforeInDays": 730,
                  "lastDownloadedBeforeInDays": 730,
                  "keepLastNVersions": 0,
                  "includedPackageVersions": ["**"],
                  "excludedPackageVersions": [],
                  "timeBasedConditions": [730, 730],
                  "versionsBasedConditions": [0],
                  "includeAllStages": true
                }
                """, JsonUtils.getInstance().writeValueAsString(criteria), JSONCompareMode.STRICT);
    }

    @Test
    void fromCriteriaDto_withVersionPatternEnabledAndEmptyIncludedVersions() throws JSONException {
        ArtifactoryHomeStub home = new ArtifactoryHomeStub(getClass().getName());
        home.loadSystemProperties();
        ArtifactoryHome.bind(home);
        
        PackageCleanupPolicySearchCriteriaDto criteriaDto = PackageCleanupPolicySearchCriteriaDto.builder()
                .repos(List.of("repo1"))
                .includedPackageVersions(List.of())
                .excludedPackageVersions(List.of("3.0.0"))
                .build();

        PackagePolicySearchCriteria criteria = PackageCleanupPolicyDtoMapper.fromCriteriaDto(criteriaDto);

        JSONAssert.assertEquals("""
                {
                  "repos": ["repo1"],
                  "createdBeforeInDays": 730,
                  "lastDownloadedBeforeInDays": 730,
                  "keepLastNVersions": 0,
                  "includedPackageVersions": ["**"],
                  "excludedPackageVersions": ["3.0.0"],
                  "timeBasedConditions": [730, 730],
                  "includeAllStages": true,
                  "versionsBasedConditions": [0]
                }
                """, JsonUtils.getInstance().writeValueAsString(criteria), JSONCompareMode.STRICT);
    }
    
    @Test
    void fromCriteriaDto_withComprehensiveFields() throws JSONException {
        PackageCleanupPolicySearchCriteriaDto criteriaDto = PackageCleanupPolicySearchCriteriaDto.builder()
                .repos(List.of("repo1", "repo2"))
                .excludedRepos(List.of("ex-repo1"))
                .includedPackages(List.of("package1", "package2"))
                .excludedPackages(List.of("ex-package1"))
                .includeAllProjects(true)
                .includedProjects(List.of("project1", "project2"))
                .packageTypes(List.of("npm", "maven"))
                .keepLastNVersions(5)
                .createdBeforeInDays(30)
                .lastDownloadedBeforeInDays(60)
                .createdBeforeInMonths(2)
                .lastDownloadedBeforeInMonths(3)
                .includedProperties(Map.of("key1", List.of("value1")))
                .excludedProperties(Map.of("key2", List.of("value2")))
                .build();

        PackagePolicySearchCriteria criteria = PackageCleanupPolicyDtoMapper.fromCriteriaDto(criteriaDto);

        JSONAssert.assertEquals("""
                {
                  "repos": ["repo1", "repo2"],
                  "excludedRepos": ["ex-repo1"],
                  "includedPackages": ["package1", "package2"],
                  "excludedPackages": ["ex-package1"],
                  "includeAllProjects": true,
                  "includedProjects": ["project1", "project2"],
                  "packageTypes": ["npm", "maven"],
                  "keepLastNVersions": 5,
                  "createdBeforeInDays": 30,
                  "lastDownloadedBeforeInDays": 60,
                  "createdBeforeInMonths": 2,
                  "lastDownloadedBeforeInMonths": 3,
                  "includedProperties": {"key1": ["value1"]},
                  "excludedProperties": {"key2": ["value2"]},
                  "includedPackageVersions": ["**"],
                  "excludedPackageVersions": [],
                  "timeBasedConditions": [2, 3, 30, 60],
                  "versionsBasedConditions": [5],
                  "includeAllStages": true
                }
                """, JsonUtils.getInstance().writeValueAsString(criteria), JSONCompareMode.STRICT);
    }

    @Test
    void toCriteriaDto_withComprehensiveFields() throws JSONException {
        PackagePolicySearchCriteria criteria = PackagePolicySearchCriteria.builder()
                .repos(List.of("repo1", "repo2"))
                .excludedRepos(List.of("ex-repo1"))
                .includedPackages(List.of("package1", "package2"))
                .excludedPackages(List.of("ex-package1"))
                .includeAllProjects(true)
                .includedProjects(List.of("project1", "project2"))
                .packageTypes(List.of("npm", "maven"))
                .keepLastNVersions(5)
                .createdBeforeInDays(30)
                .lastDownloadedBeforeInDays(60)
                .createdBeforeInMonths(2)
                .lastDownloadedBeforeInMonths(3)
                .includedProperties(Map.of("key1", List.of("value1")))
                .excludedProperties(Map.of("key2", List.of("value2")))
                .build();

        PackageCleanupPolicySearchCriteriaDto dto = PackageCleanupPolicyDtoMapper.toCriteriaDto(criteria);

        JSONAssert.assertEquals("""
                {
                  "repos": ["repo1", "repo2"],
                  "excludedRepos": ["ex-repo1"],
                  "includedPackages": ["package1", "package2"],
                  "excludedPackages": ["ex-package1"],
                  "includeAllProjects": true,
                  "includedProjects": ["project1", "project2"],
                  "includeAllStages": true,
                  "packageTypes": ["npm", "maven"],
                  "keepLastNVersions": 5,
                  "createdBeforeInDays": 30,
                  "lastDownloadedBeforeInDays": 60,
                  "createdBeforeInMonths": 2,
                  "lastDownloadedBeforeInMonths": 3,
                  "includedProperties": {"key1": ["value1"]},
                  "excludedProperties": {"key2": ["value2"]},
                  "includedPackageVersions": ["**"],
                  "excludedPackageVersions": []
                }
                """, JsonUtils.getInstance().writeValueAsString(dto), JSONCompareMode.STRICT);
    }

    @Test
    void toViewCriteriaDto_withoutReposWithStatus() throws JSONException {
        PackagePolicySearchCriteria criteria = PackagePolicySearchCriteria.builder()
                .repos(List.of("repo1"))
                .build();

        PackageCleanupPolicySearchCriteriaDto dto = PackageCleanupPolicyDtoMapper.toViewCriteriaDto(criteria);

        JSONAssert.assertEquals("""
                {
                  "repos": ["repo1"],
                  "createdBeforeInDays": 730,
                  "lastDownloadedBeforeInDays": 730,
                  "keepLastNVersions": 0,
                  "includedPackageVersions": ["**"],
                  "excludedPackageVersions": [],
                  "includeAllStages": true
                }
                """, JsonUtils.getInstance().writeValueAsString(dto), JSONCompareMode.STRICT);
    }

    @Test
    void toCriteriaDto_withNullIncludedPackageVersions() throws JSONException {
        PackagePolicySearchCriteria criteria = PackagePolicySearchCriteria.builder()
                .repos(List.of("repo1"))
                .includedPackageVersions(null)
                .excludedPackageVersions(null)
                .build();

        PackageCleanupPolicySearchCriteriaDto dto = PackageCleanupPolicyDtoMapper.toCriteriaDto(criteria);

        JSONAssert.assertEquals("""
                {
                  "repos": ["repo1"],
                  "createdBeforeInDays": 730,
                  "lastDownloadedBeforeInDays": 730,
                  "keepLastNVersions": 0,
                  "includedPackageVersions": ["**"],
                  "excludedPackageVersions": [],
                  "includeAllStages": true
                }
                """, JsonUtils.getInstance().writeValueAsString(dto), JSONCompareMode.STRICT);
    }

    @Test
    void toCriteriaDto_withEmptyIncludedPackageVersions() throws JSONException {
        PackagePolicySearchCriteria criteria = PackagePolicySearchCriteria.builder()
                .repos(List.of("repo1"))
                .includedPackageVersions(List.of())
                .excludedPackageVersions(List.of())
                .build();

        PackageCleanupPolicySearchCriteriaDto dto = PackageCleanupPolicyDtoMapper.toCriteriaDto(criteria);

        JSONAssert.assertEquals("""
                {
                  "repos": ["repo1"],
                  "createdBeforeInDays": 730,
                  "lastDownloadedBeforeInDays": 730,
                  "keepLastNVersions": 0,
                  "includedPackageVersions": ["**"],
                  "excludedPackageVersions": [],
                  "includeAllStages": true
                }
                """, JsonUtils.getInstance().writeValueAsString(dto), JSONCompareMode.STRICT);
    }

    @Test
    void toCriteriaDto_withNonNullExcludedPackageVersions() throws JSONException {
        PackagePolicySearchCriteria criteria = PackagePolicySearchCriteria.builder()
                .repos(List.of("repo1"))
                .includedPackageVersions(List.of("1.0.0"))
                .excludedPackageVersions(List.of("3.0.0"))
                .build();

        PackageCleanupPolicySearchCriteriaDto dto = PackageCleanupPolicyDtoMapper.toCriteriaDto(criteria);

        JSONAssert.assertEquals("""
                {
                  "repos": ["repo1"],
                  "createdBeforeInDays": 730,
                  "lastDownloadedBeforeInDays": 730,
                  "keepLastNVersions": 0,
                  "includedPackageVersions": ["1.0.0"],
                  "excludedPackageVersions": ["3.0.0"],
                  "includeAllStages": true
                }
                """, JsonUtils.getInstance().writeValueAsString(dto), JSONCompareMode.STRICT);
    }

    @Test
    void toViewCriteriaDto_withReposWithStatus() throws JSONException {
        PackagePolicySearchCriteria criteria = PackagePolicySearchCriteria.builder()
                .repos(List.of("repo1"))
                .reposWithStatus(List.of(
                        new RepoAvailabilityStatus("repo1", true),
                        new RepoAvailabilityStatus("repo2", false)))
                .build();

        PackageCleanupPolicySearchCriteriaDto dto = PackageCleanupPolicyDtoMapper.toViewCriteriaDto(criteria);

        JSONAssert.assertEquals("""
                {
                  "repos": ["repo1"],
                  "createdBeforeInDays": 730,
                  "lastDownloadedBeforeInDays": 730,
                  "keepLastNVersions": 0,
                  "includedPackageVersions": ["**"],
                  "excludedPackageVersions": [],
                  "includeAllStages": true,
                  "reposWithStatus": [
                    {"repoName": "repo1", "isAvailable": true},
                    {"repoName": "repo2", "isAvailable": false}
                  ]
                }
                """, JsonUtils.getInstance().writeValueAsString(dto), JSONCompareMode.STRICT);
    }

    @Test
    void toDto_withIncludeAllStagesFalseAndIncludedStages() throws JSONException {
        PackageCleanupPolicy packageCleanupPolicy = PackageCleanupPolicy.builder()
                .key("key")
                .type(PolicyType.CLEANUP)
                .itemType(PolicyItemType.PACKAGE)
                .created(1L)
                .createdBy("test")
                .modified(1L)
                .modifiedBy("test2")
                .cronExp("1 1 * * * ?")
                .enabled(true)
                .description("desc")
                .config(PackageCleanupPolicyConfig.builder()
                        .skipTrashcan(true)
                        .durationInMinutes(1)
                        .searchCriteria(PackagePolicySearchCriteria.builder()
                                .repos(List.of("repo1", "repo2"))
                                .excludedRepos(List.of("ex-repo3", "ex-repo4"))
                                .includedPackages(List.of("package1"))
                                .excludedPackages(List.of("ex-package2", "ex-package3"))
                                .includeAllStages(false)
                                .includedStages(List.of("stage1", "stage2"))
                                .lastDownloadedBeforeInMonths(1)
                                .createdBeforeInMonths(1)
                                .build())
                        .build())
                .build();

        PackageCleanupPolicyDto dto = PackageCleanupPolicyDtoMapper.toDto(packageCleanupPolicy);
        JSONAssert.assertEquals("""
                                {
                                  "key": "key",
                                  "description": "desc",
                                  "cronExp": "1 1 * * * ?",
                                  "durationInMinutes": 1,
                                  "itemType": "package",
                                  "enabled": true,
                                  "skipTrashcan": true,
                                  "searchCriteria": {
                                    "repos": [
                                      "repo1",
                                      "repo2"
                                    ],
                                    "excludedRepos": [
                                      "ex-repo3",
                                      "ex-repo4"
                                    ],
                                    "includedPackages": [
                                      "package1"
                                    ],
                                    "excludedPackages": [
                                      "ex-package2",
                                      "ex-package3"
                                    ],
                                    "includedPackageVersions": ["**"],
                                    "excludedPackageVersions": [],
                                    "includeAllStages": false,
                                    "includedStages": ["stage1", "stage2"],
                                    "createdBeforeInMonths": 1,
                                    "lastDownloadedBeforeInMonths": 1
                                  }
                                }
                """, JsonUtils.getInstance().writeValueAsString(dto), JSONCompareMode.STRICT);
    }

    @Test
    void fromDto_withIncludeAllStagesFalseAndIncludedStages() throws JSONException {
        PackageCleanupPolicyDto dto = PackageCleanupPolicyDto.builder()
                .key("key")
                .searchCriteria(PackageCleanupPolicySearchCriteriaDto
                        .builder()
                        .repos(List.of("repo1", "repo2"))
                        .excludedRepos(List.of("ex-repo3", "ex-repo4"))
                        .includedPackages(List.of("package1"))
                        .excludedPackages(List.of("ex-package2", "ex-package3"))
                        .includeAllStages(false)
                        .includedStages(List.of("stage1", "stage2"))
                        .lastDownloadedBeforeInMonths(1)
                        .createdBeforeInMonths(1)
                        .build())
                .enabled(true)
                .cronExp("1 1 * * * ?")
                .description("desc")
                .durationInMinutes(1)
                .skipTrashcan(true)
                .build();

        PackageCleanupPolicy packageCleanupPolicy = PackageCleanupPolicyDtoMapper.fromDto(dto);

        JSONAssert.assertEquals("""
                        {
                          "key" : "key",
                          "type" : "CLEANUP",
                          "itemType" : "PACKAGE",
                          "enabled" : true,
                          "created" : 0,
                          "cronExp" : "1 1 * * * ?",
                          "description" : "desc",
                          "config" : {
                            "skipTrashcan" : true,
                            "durationInMinutes" : 1,
                            "searchCriteria" : {
                              "repos" : [ "repo1", "repo2" ],
                              "excludedRepos" : [ "ex-repo3", "ex-repo4" ],
                              "includedPackages" : [ "package1" ],
                              "excludedPackages" : [ "ex-package2", "ex-package3" ],
                              "includeAllStages" : false,
                              "includedStages" : [ "stage1", "stage2" ],
                              "includedPackageVersions" : [ "**" ],
                              "excludedPackageVersions" : [ ],
                              "createdBeforeInMonths" : 1,
                              "lastDownloadedBeforeInMonths" : 1,
                              "timeBasedConditions":[1,1],
                              "versionsBasedConditions":[]
                            }
                          }
                        }
                        """, JsonUtils.getInstance().writeValueAsString(packageCleanupPolicy),
                JSONCompareMode.STRICT);
    }

    @Test
    void toCriteriaDto_withIncludeAllStagesFalseAndIncludedStages() throws JSONException {
        PackagePolicySearchCriteria criteria = PackagePolicySearchCriteria.builder()
                .repos(List.of("repo1"))
                .includeAllStages(false)
                .includedStages(List.of("stage1", "stage2", "stage3"))
                .build();

        PackageCleanupPolicySearchCriteriaDto dto = PackageCleanupPolicyDtoMapper.toCriteriaDto(criteria);

        JSONAssert.assertEquals("""
                {
                  "repos": ["repo1"],
                  "createdBeforeInDays": 730,
                  "lastDownloadedBeforeInDays": 730,
                  "keepLastNVersions": 0,
                  "includedPackageVersions": ["**"],
                  "excludedPackageVersions": [],
                  "includeAllStages": false,
                  "includedStages": ["stage1", "stage2", "stage3"]
                }
                """, JsonUtils.getInstance().writeValueAsString(dto), JSONCompareMode.STRICT);
    }

    @Test
    void toCriteriaDto_withNullIncludeAllStagesAndNullIncludedStages_defaultsToTrue() throws JSONException {
        // Test case: when both includeAllStages and includedStages are null, default to includeAllStages=true
        PackagePolicySearchCriteria criteria = PackagePolicySearchCriteria.builder()
                .repos(List.of("repo1"))
                .includeAllStages(null)
                .includedStages(null)
                .build();

        PackageCleanupPolicySearchCriteriaDto dto = PackageCleanupPolicyDtoMapper.toCriteriaDto(criteria);

        JSONAssert.assertEquals("""
                {
                  "repos": ["repo1"],
                  "createdBeforeInDays": 730,
                  "lastDownloadedBeforeInDays": 730,
                  "keepLastNVersions": 0,
                  "includedPackageVersions": ["**"],
                  "excludedPackageVersions": [],
                  "includeAllStages": true
                }
                """, JsonUtils.getInstance().writeValueAsString(dto), JSONCompareMode.STRICT);
    }

    @Test
    void toCriteriaDto_withNullIncludeAllStagesAndEmptyIncludedStages_defaultsToTrue() throws JSONException {
        // Test case: when includeAllStages is null and includedStages is empty, default to includeAllStages=true
        PackagePolicySearchCriteria criteria = PackagePolicySearchCriteria.builder()
                .repos(List.of("repo1"))
                .includeAllStages(null)
                .includedStages(List.of())
                .build();

        PackageCleanupPolicySearchCriteriaDto dto = PackageCleanupPolicyDtoMapper.toCriteriaDto(criteria);

        JSONAssert.assertEquals("""
                {
                  "repos": ["repo1"],
                  "createdBeforeInDays": 730,
                  "lastDownloadedBeforeInDays": 730,
                  "keepLastNVersions": 0,
                  "includedPackageVersions": ["**"],
                  "excludedPackageVersions": [],
                  "includeAllStages": true,
                  "includedStages": []
                }
                """, JsonUtils.getInstance().writeValueAsString(dto), JSONCompareMode.STRICT);
    }

    @Test
    void toViewCriteriaDto_withNullIncludeAllStagesAndNullIncludedStages_defaultsToTrue() throws JSONException {
        // Test case: when both includeAllStages and includedStages are null, default to includeAllStages=true
        PackagePolicySearchCriteria criteria = PackagePolicySearchCriteria.builder()
                .repos(List.of("repo1"))
                .includeAllStages(null)
                .includedStages(null)
                .build();

        PackageCleanupPolicySearchCriteriaDto dto = PackageCleanupPolicyDtoMapper.toViewCriteriaDto(criteria);

        JSONAssert.assertEquals("""
                {
                  "repos": ["repo1"],
                  "createdBeforeInDays": 730,
                  "lastDownloadedBeforeInDays": 730,
                  "keepLastNVersions": 0,
                  "includedPackageVersions": ["**"],
                  "excludedPackageVersions": [],
                  "includeAllStages": true
                }
                """, JsonUtils.getInstance().writeValueAsString(dto), JSONCompareMode.STRICT);
    }

    @Test
    void toCriteriaDto_withExplicitIncludeAllStagesFalse_preservesValue() throws JSONException {
        // Test case: when includeAllStages is explicitly set to false (not null), use the value directly
        // This covers the else branch: criteria.getIncludeAllStages()
        PackagePolicySearchCriteria criteria = PackagePolicySearchCriteria.builder()
                .repos(List.of("repo1"))
                .includeAllStages(false)
                .includedStages(List.of("stage1"))
                .build();

        PackageCleanupPolicySearchCriteriaDto dto = PackageCleanupPolicyDtoMapper.toCriteriaDto(criteria);

        JSONAssert.assertEquals("""
                {
                  "repos": ["repo1"],
                  "createdBeforeInDays": 730,
                  "lastDownloadedBeforeInDays": 730,
                  "keepLastNVersions": 0,
                  "includedPackageVersions": ["**"],
                  "excludedPackageVersions": [],
                  "includeAllStages": false,
                  "includedStages": ["stage1"]
                }
                """, JsonUtils.getInstance().writeValueAsString(dto), JSONCompareMode.STRICT);
    }

    @Test
    void toCriteriaDto_withFolderPaths() throws JSONException {
        PackagePolicySearchCriteria criteria = PackagePolicySearchCriteria.builder()
                .repos(List.of("repo1"))
                .includedFolderPaths(Set.of("releases/*", "*/production"))
                .excludedFolderPaths(Set.of("snapshots/*", "*/dev"))
                .build();

        PackageCleanupPolicySearchCriteriaDto dto = PackageCleanupPolicyDtoMapper.toCriteriaDto(criteria);

        JSONAssert.assertEquals("""
                {
                  "repos": ["repo1"],
                  "createdBeforeInDays": 730,
                  "lastDownloadedBeforeInDays": 730,
                  "keepLastNVersions": 0,
                  "includedPackageVersions": ["**"],
                  "excludedPackageVersions": [],
                  "includeAllStages": true,
                  "includedFolderPaths": ["releases/*", "*/production"],
                  "excludedFolderPaths": ["snapshots/*", "*/dev"]
                }
                """, JsonUtils.getInstance().writeValueAsString(dto), JSONCompareMode.LENIENT);
    }

    @Test
    void toCriteriaDto_withNullFolderPaths() throws JSONException {
        PackagePolicySearchCriteria criteria = PackagePolicySearchCriteria.builder()
                .repos(List.of("repo1"))
                .includedFolderPaths(null)
                .excludedFolderPaths(null)
                .build();

        PackageCleanupPolicySearchCriteriaDto dto = PackageCleanupPolicyDtoMapper.toCriteriaDto(criteria);

        JSONAssert.assertEquals("""
                {
                  "repos": ["repo1"],
                  "createdBeforeInDays": 730,
                  "lastDownloadedBeforeInDays": 730,
                  "keepLastNVersions": 0,
                  "includedPackageVersions": ["**"],
                  "excludedPackageVersions": [],
                  "includeAllStages": true
                }
                """, JsonUtils.getInstance().writeValueAsString(dto), JSONCompareMode.STRICT);
    }

    @Test
    void fromCriteriaDto_withFolderPaths() throws JSONException {
        PackageCleanupPolicySearchCriteriaDto criteriaDto = PackageCleanupPolicySearchCriteriaDto.builder()
                .repos(List.of("repo1"))
                .includedFolderPaths(Set.of("releases/*", "*/production"))
                .excludedFolderPaths(Set.of("snapshots/*"))
                .build();

        PackagePolicySearchCriteria criteria = PackageCleanupPolicyDtoMapper.fromCriteriaDto(criteriaDto);

        JSONAssert.assertEquals("""
                {
                  "repos": ["repo1"],
                  "createdBeforeInDays": 730,
                  "lastDownloadedBeforeInDays": 730,
                  "keepLastNVersions": 0,
                  "includedPackageVersions": ["**"],
                  "excludedPackageVersions": [],
                  "timeBasedConditions": [730, 730],
                  "versionsBasedConditions": [0],
                  "includeAllStages": true,
                  "includedFolderPaths": ["releases/*", "*/production"],
                  "excludedFolderPaths": ["snapshots/*"]
                }
                """, JsonUtils.getInstance().writeValueAsString(criteria), JSONCompareMode.LENIENT);
    }

    @Test
    void fromCriteriaDto_withNullFolderPaths() throws JSONException {
        PackageCleanupPolicySearchCriteriaDto criteriaDto = PackageCleanupPolicySearchCriteriaDto.builder()
                .repos(List.of("repo1"))
                .includedFolderPaths(null)
                .excludedFolderPaths(null)
                .build();

        PackagePolicySearchCriteria criteria = PackageCleanupPolicyDtoMapper.fromCriteriaDto(criteriaDto);

        JSONAssert.assertEquals("""
                {
                  "repos": ["repo1"],
                  "createdBeforeInDays": 730,
                  "lastDownloadedBeforeInDays": 730,
                  "keepLastNVersions": 0,
                  "includedPackageVersions": ["**"],
                  "excludedPackageVersions": [],
                  "timeBasedConditions": [730, 730],
                  "versionsBasedConditions": [0],
                  "includeAllStages": true
                }
                """, JsonUtils.getInstance().writeValueAsString(criteria), JSONCompareMode.STRICT);
    }

    @Test
    void toViewCriteriaDto_withFolderPaths() throws JSONException {
        PackagePolicySearchCriteria criteria = PackagePolicySearchCriteria.builder()
                .repos(List.of("repo1"))
                .includedFolderPaths(Set.of("com/jfrog/*"))
                .excludedFolderPaths(Set.of("*/test"))
                .reposWithStatus(List.of(new RepoAvailabilityStatus("repo1", true)))
                .build();

        PackageCleanupPolicySearchCriteriaDto dto = PackageCleanupPolicyDtoMapper.toViewCriteriaDto(criteria);

        JSONAssert.assertEquals("""
                {
                  "repos": ["repo1"],
                  "createdBeforeInDays": 730,
                  "lastDownloadedBeforeInDays": 730,
                  "keepLastNVersions": 0,
                  "includedPackageVersions": ["**"],
                  "excludedPackageVersions": [],
                  "includeAllStages": true,
                  "includedFolderPaths": ["com/jfrog/*"],
                  "excludedFolderPaths": ["*/test"],
                  "reposWithStatus": [
                    {"repoName": "repo1", "isAvailable": true}
                  ]
                }
                """, JsonUtils.getInstance().writeValueAsString(dto), JSONCompareMode.LENIENT);
    }

    @Test
    void toDto_withFolderPaths() throws JSONException {
        PackageCleanupPolicy packageCleanupPolicy = PackageCleanupPolicy.builder()
                .key("key")
                .type(PolicyType.CLEANUP)
                .itemType(PolicyItemType.PACKAGE)
                .created(1L)
                .createdBy("test")
                .modified(1L)
                .modifiedBy("test2")
                .cronExp("1 1 * * * ?")
                .enabled(true)
                .description("desc")
                .config(PackageCleanupPolicyConfig.builder()
                        .skipTrashcan(true)
                        .durationInMinutes(1)
                        .searchCriteria(PackagePolicySearchCriteria.builder()
                                .repos(List.of("repo1"))
                                .includedFolderPaths(Set.of("releases/*"))
                                .excludedFolderPaths(Set.of("snapshots/*"))
                                .lastDownloadedBeforeInMonths(1)
                                .createdBeforeInMonths(1)
                                .build())
                        .build())
                .build();

        PackageCleanupPolicyDto dto = PackageCleanupPolicyDtoMapper.toDto(packageCleanupPolicy);

        JSONAssert.assertEquals("""
                {
                  "key": "key",
                  "description": "desc",
                  "cronExp": "1 1 * * * ?",
                  "durationInMinutes": 1,
                  "itemType": "package",
                  "enabled": true,
                  "skipTrashcan": true,
                  "searchCriteria": {
                    "repos": ["repo1"],
                    "includedPackageVersions": ["**"],
                    "excludedPackageVersions": [],
                    "includeAllStages": true,
                    "createdBeforeInMonths": 1,
                    "lastDownloadedBeforeInMonths": 1,
                    "includedFolderPaths": ["releases/*"],
                    "excludedFolderPaths": ["snapshots/*"]
                  }
                }
                """, JsonUtils.getInstance().writeValueAsString(dto), JSONCompareMode.LENIENT);
    }

    @Test
    void fromDto_withFolderPaths() throws JSONException {
        PackageCleanupPolicyDto dto = PackageCleanupPolicyDto.builder()
                .key("key")
                .searchCriteria(PackageCleanupPolicySearchCriteriaDto
                        .builder()
                        .repos(List.of("repo1"))
                        .includedFolderPaths(Set.of("*/production", "releases/*"))
                        .excludedFolderPaths(Set.of("*/dev"))
                        .lastDownloadedBeforeInMonths(1)
                        .createdBeforeInMonths(1)
                        .build())
                .enabled(true)
                .cronExp("1 1 * * * ?")
                .description("desc")
                .durationInMinutes(1)
                .skipTrashcan(true)
                .build();

        PackageCleanupPolicy packageCleanupPolicy = PackageCleanupPolicyDtoMapper.fromDto(dto);

        JSONAssert.assertEquals("""
                {
                  "key": "key",
                  "type": "CLEANUP",
                  "itemType": "PACKAGE",
                  "enabled": true,
                  "created": 0,
                  "cronExp": "1 1 * * * ?",
                  "description": "desc",
                  "config": {
                    "skipTrashcan": true,
                    "durationInMinutes": 1,
                    "searchCriteria": {
                      "repos": ["repo1"],
                      "includedPackageVersions": ["**"],
                      "excludedPackageVersions": [],
                      "createdBeforeInMonths": 1,
                      "lastDownloadedBeforeInMonths": 1,
                      "timeBasedConditions": [1, 1],
                      "versionsBasedConditions": [],
                      "includeAllStages": true,
                      "includedFolderPaths": ["*/production", "releases/*"],
                      "excludedFolderPaths": ["*/dev"]
                    }
                  }
                }
                """, JsonUtils.getInstance().writeValueAsString(packageCleanupPolicy), JSONCompareMode.LENIENT);
    }

    @Test
    void fromCriteriaDto_shouldTrimFolderPaths() throws JSONException {
        PackageCleanupPolicySearchCriteriaDto criteriaDto = PackageCleanupPolicySearchCriteriaDto.builder()
                .repos(List.of("repo1"))
                .includedFolderPaths(Set.of("  releases/*  ", "  */production  "))
                .excludedFolderPaths(Set.of("  snapshots/*  "))
                .build();

        PackagePolicySearchCriteria criteria = PackageCleanupPolicyDtoMapper.fromCriteriaDto(criteriaDto);

        JSONAssert.assertEquals("""
                {
                  "repos": ["repo1"],
                  "includedPackageVersions": ["**"],
                  "excludedPackageVersions": [],
                  "versionsBasedConditions": [0],
                  "includeAllStages": true,
                  "includedFolderPaths": ["releases/*", "*/production"],
                  "excludedFolderPaths": ["snapshots/*"]
                }
                """, JsonUtils.getInstance().writeValueAsString(criteria), JSONCompareMode.LENIENT);
    }

}
