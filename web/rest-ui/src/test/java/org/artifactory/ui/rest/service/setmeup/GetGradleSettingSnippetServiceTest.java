package org.artifactory.ui.rest.service.setmeup;

import com.fasterxml.jackson.core.util.VersionUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.SneakyThrows;
import org.artifactory.api.security.SecurityService;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.StreamRestResponse;
import org.artifactory.ui.rest.model.setmeup.GradleSettingModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Properties;

import static org.artifactory.ui.rest.service.setmeup.GetSettingSnippetService.READABLE_ARTIFACTORY_ESCAPED_PASSWORD_TEMPLATE;
import static org.artifactory.ui.rest.service.setmeup.GetSettingSnippetService.READABLE_ARTIFACTORY_PASSWORD_TEMPLATE;
import static org.artifactory.ui.rest.service.setmeup.GetSettingSnippetService.READABLE_ARTIFACTORY_USER_NAME_TEMPLATE;
import static org.fest.assertions.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * @author Natalie Berni
 */
public class GetGradleSettingSnippetServiceTest extends GetSettingSnippetServiceTest {
    private GetGradleSettingSnippetService getGradleSettingSnippetService;

    @BeforeEach
    @Override
    void setUp() {
        super.setUp();
        getGradleSettingSnippetService = Mockito.spy(new GetGradleSettingSnippetService());
    }

    @Test
    void testExecuteDownloadScript() {
        when(request.getQueryParamByKey("downloadScript")).thenReturn("true");
        when(request.getQueryParamByKey("deploy")).thenReturn("false");

        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getContextPath()).thenReturn("");
        when(servletRequest.getHeader(ArtifactoryRequest.JFROG_OVERRIDE_BASE_URL)).thenReturn(BASE_URL);

        when(request.getServletRequest()).thenReturn(servletRequest);
        doReturn("http://localhost:8081/").when(getGradleSettingSnippetService)
                .generateSettings(anyString(), any(), any());
        mockBaseUrlFromConfig();
        when(sysConfig.helper()).thenReturn(sysConfigHelper);
        when(sysConfigHelper.getRouterExternalPort()).thenReturn(-1);

        when(request.getImodel()).thenReturn(new GradleSettingModel());
        StreamRestResponse restResponse = new StreamRestResponse();
        getGradleSettingSnippetService.execute(request, restResponse);
        assertThat(restResponse.getEntity()).isInstanceOf(String.class);
    }

    @Test
    void verifyCredentialsAreWhenGeneratingAToken() throws Exception {
        prepareGradleUpdateSettingModel();

        GradleSettingModel model = new GradleSettingModel();
        model.setPassword("mytoken");
        when(request.getImodel()).thenReturn(model);
        String modelAfterFilter = """
                                buildscript {
                                    repositories {
                                        jcenter()
                                    }
                                    dependencies {
                            //Check for the latest version here: http://plugins.gradle.org/plugin/com.jfrog.artifactory
                                        classpath "org.jfrog.buildinfo:build-info-extractor-gradle:4+"
                                    }
                                }
                                
                                allprojects {
                                    apply plugin: "com.jfrog.artifactory"
                                }
                                
                                artifactory {
                                    contextUrl = "${artifactory_contextUrl}"
                                    //The base Artifactory URL if not overridden by the publisher/resolver
                                    publish {
                                        repository {
                                            repoKey = 'a-gradle-dev-local'
                                            username = "${artifactory_user}"
                                            password = "${artifactory_password}"
                                            maven = true
                                        }
                                    }
                                    resolve {
                                        repository {
                                            repoKey = 'a-gradle-dev'
                                            username = "${artifactory_user}"
                                            password = "${artifactory_password}"
                                            maven = true
                                        }
                                    }
                                }
                """;
        when(filteredResourcesWebAddon.filterResource(any(), any(), any())).thenReturn(modelAfterFilter);

        ArtifactoryRestResponse restResponse = new ArtifactoryRestResponse<>();
        getGradleSettingSnippetService.execute(request, restResponse);

        assertThat(restResponse.getIModel()).isInstanceOf(GradleSettingModel.class);
        String gradleSnippetResult = ((GradleSettingModel) restResponse.getIModel()).getGradleSnippet();
        assertThat(gradleSnippetResult.contains(READABLE_ARTIFACTORY_USER_NAME_TEMPLATE)).isFalse();
        assertThat(gradleSnippetResult.contains(READABLE_ARTIFACTORY_ESCAPED_PASSWORD_TEMPLATE)).isFalse();
        assertThat(gradleSnippetResult.contains(TEMPLATE_ESCAPED_CREDENTIALS)).isFalse();
        assertThat(gradleSnippetResult.contains(TEMPLATE_NAME)).isFalse();
    }

    @Test
    void verifyCredentialsAreNotAddedDuringDismiss() {
        prepareGradleUpdateSettingModel();

        when(request.getImodel()).thenReturn(new GradleSettingModel());

        ArtifactoryRestResponse restResponse = new ArtifactoryRestResponse<>();
        getGradleSettingSnippetService.execute(request, restResponse);

        assertThat(restResponse.getIModel()).isInstanceOf(GradleSettingModel.class);
        String gradleSnippetResult = ((GradleSettingModel) restResponse.getIModel()).getGradleSnippet();
        assertThat(gradleSnippetResult.contains(READABLE_ARTIFACTORY_USER_NAME_TEMPLATE)).isTrue();
        assertThat(gradleSnippetResult.contains(READABLE_ARTIFACTORY_PASSWORD_TEMPLATE)).isTrue();
        assertThat(gradleSnippetResult.contains(READABLE_ARTIFACTORY_PASSWORD_TEMPLATE.toLowerCase())).isFalse();
        assertThat(gradleSnippetResult.contains(READABLE_ARTIFACTORY_PASSWORD_TEMPLATE.toLowerCase())).isFalse();
    }

    private void prepareGradleUpdateSettingModel() {
        when(request.getQueryParamByKey("downloadScript")).thenReturn("false");
        when(request.getQueryParamByKey("deploy")).thenReturn("false");

        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getContextPath()).thenReturn("");
        when(servletRequest.getHeader(ArtifactoryRequest.JFROG_OVERRIDE_BASE_URL)).thenReturn(BASE_URL);

        when(request.getServletRequest()).thenReturn(servletRequest);
        String gradleSnippet = """
                        buildscript {
                                    repositories {
                                        jcenter()
                                    }
                                    dependencies {
                            //Check for the latest version here: http://plugins.gradle.org/plugin/com.jfrog.artifactory
                                        classpath "org.jfrog.buildinfo:build-info-extractor-gradle:4+"
                                    }
                                }
                                
                                allprojects {
                                    apply plugin: "com.jfrog.artifactory"
                                }
                                
                                artifactory {
                                    contextUrl = "${artifactory_contextUrl}"
                                    //The base Artifactory URL if not overridden by the publisher/resolver
                                    publish {
                                        repository {
                                            repoKey = 'a-gradle-dev-local'
                                            username = "${artifactory_user}"
                                            password = "${artifactory_password}"
                                            maven = true
                                        }
                                    }
                                    resolve {
                                        repository {
                                            repoKey = 'a-gradle-dev'
                                            username = "${artifactory_user}"
                                            password = "${artifactory_password}"
                                            maven = true
                                        }
                                    }
                                }
                     
                """;
        doReturn(gradleSnippet).when(getGradleSettingSnippetService)
                .generateSettings(anyString(), any(), any());
        mockBaseUrlFromConfig();
        when(sysConfig.helper()).thenReturn(sysConfigHelper);
        when(sysConfigHelper.getRouterExternalPort()).thenReturn(-1);
    }

    @SneakyThrows
    @ParameterizedTest
    @CsvSource({
            "6.6, '4+'",
            "7, '5+'",
            "7.9.9, '5+'",
            "8, '6+'",
            "8.1, '6+'",
            "9.0.0, '6+'",
            ", '6+'",
            "'', '6+'"
    })
    void testBuildInfoVersion(String gradleVersion, String expectedBuildInfoVersion) {
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getHeader(ArtifactoryRequest.JFROG_OVERRIDE_BASE_URL)).thenReturn(BASE_URL);
        when(request.getServletRequest()).thenReturn(servletRequest);

        GradleSettingModel model = getGradleSettingModel(gradleVersion);
        when(request.getImodel()).thenReturn(model);

        SecurityService mockSecurityService = mock(SecurityService.class);
        when(mockSecurityService.isAnonymous()).thenReturn(true);
        when(mockSecurityService.isAnonAccessEnabled()).thenReturn(true);
        when(sysConfig.helper()).thenReturn(sysConfigHelper);

        Properties[] capturedProperties = new Properties[1];
        getGradleSettingSnippetService = new GetGradleSettingSnippetService() {
            @Override
            public String generateSettings(String servletContextUrl, GradleSettingModel gradleSettingModel,
                    RestResponse response) {
                try {
                    securityService = mockSecurityService;
                    capturedProperties[0] = getTemplateProperties(servletContextUrl, gradleSettingModel, response);
                    return "";
                } catch (Exception e) {
                    throw new RuntimeException("Failed to call getTemplateProperties", e);
                }
            }
        };

        getGradleSettingSnippetService.execute(request, new ArtifactoryRestResponse<>());

        assertEquals(expectedBuildInfoVersion, capturedProperties[0].getProperty("buildinfo.version"));
    }

    @Test
    void testBuildInfoVersionWithInvalidVersion() {
        String invalidGradleVersion = "vresion!@#$56.25";
        try (MockedStatic<VersionUtil> utils = mockStatic(VersionUtil.class)) {
            utils.when(() -> VersionUtil.parseVersion(invalidGradleVersion, null, null))
                    .thenThrow(new RuntimeException("Invalid version format"));
            testBuildInfoVersion(invalidGradleVersion, "6+");
        }
    }

    private static GradleSettingModel getGradleSettingModel(String gradleVersion) {
        GradleSettingModel model = new GradleSettingModel();
        model.setGradleVersion(gradleVersion);
        model.setPluginRepoKey("Bintray -> jcenter");
        model.setLibsResolverRepoKey("resolver-repo");
        model.setLibsPublisherRepoKey("publisher-repo");
        model.setPluginUseMaven(false);
        model.setResolverUseMaven(false);
        model.setPublisherUseMaven(false);
        model.setPluginUseIvy(false);
        model.setResolverUseIvy(false);
        model.setPublisherUseIvy(false);
        return model;
    }

    private Properties getTemplateProperties(String servletContextUrl, GradleSettingModel gradleSettingModel,
            RestResponse response)
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Method method = GetGradleSettingSnippetService.class.getDeclaredMethod(
                "getTemplateProperties", String.class, GradleSettingModel.class, RestResponse.class);
        method.setAccessible(true);
        return (Properties) method.invoke(getGradleSettingSnippetService, servletContextUrl, gradleSettingModel,
                response);
    }
}
