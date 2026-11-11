package org.artifactory.ui.rest.service.setmeup;

import jakarta.servlet.http.HttpServletRequest;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.common.service.StreamRestResponse;
import org.artifactory.ui.rest.model.setmeup.IvySettingModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.artifactory.ui.rest.service.setmeup.GetSettingSnippetService.READABLE_ARTIFACTORY_ESCAPED_PASSWORD_TEMPLATE;
import static org.artifactory.ui.rest.service.setmeup.GetSettingSnippetService.READABLE_ARTIFACTORY_PASSWORD_TEMPLATE;
import static org.artifactory.ui.rest.service.setmeup.GetSettingSnippetService.READABLE_ARTIFACTORY_USER_NAME_TEMPLATE;
import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Natalie Berni
 */
public class GetIvySettingSnippetServiceTest extends GetSettingSnippetServiceTest {

    private GetIvySettingSnippetService getIvySettingSnippetService;

    @BeforeEach
    @Override
    void setUp() {
        super.setUp();
        getIvySettingSnippetService = Mockito.spy(new GetIvySettingSnippetService());
    }

    @Test
    void testExecuteDownloadScript() {
        when(request.getQueryParamByKey("downloadScript")).thenReturn("true");
        when(request.getQueryParamByKey("deploy")).thenReturn("false");

        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getContextPath()).thenReturn("");
        when(servletRequest.getHeader(ArtifactoryRequest.JFROG_OVERRIDE_BASE_URL)).thenReturn(BASE_URL);

        when(request.getServletRequest()).thenReturn(servletRequest);
        doReturn("http://localhost:8081/").when(getIvySettingSnippetService)
                .generateSettings(anyString(), any(), any());
        mockBaseUrlFromConfig();
        when(sysConfig.helper()).thenReturn(sysConfigHelper);
        when(sysConfigHelper.getRouterExternalPort()).thenReturn(-1);

        when(request.getImodel()).thenReturn(new IvySettingModel());
        StreamRestResponse restResponse = new StreamRestResponse();
        getIvySettingSnippetService.execute(request, restResponse);
        assertThat(restResponse.getEntity()).isInstanceOf(String.class);
    }



    @Test
    void verifyCredentialsAreWhenGeneratingAToken() throws Exception {
        prepareIvyUpdateSettingModel();

        IvySettingModel model = new IvySettingModel();
        model.setPassword("mytoken");
        when(request.getImodel()).thenReturn(model);
        String modelAfterFilter = """
                String ivySnippet = ""\"
                                <?xml version="1.0" encoding="UTF-8"?>
                                <settings xsi:schemaLocation=
                                "http://maven.apache.org/SETTINGS/1.2.0 http://maven.apache.org/xsd/settings-1.2.0.xsd"
                                xmlns="http://maven.apache.org/SETTINGS/1.2.0"
                                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                                  <servers>
                                    <server>
                                 <username>admin</username>
                                 <password>*** Insert encrypted password here ***</password>
                                      <id>central</id>
                                    </server>
                                    <server>
                                 <username>admin</username>
                                 <password>*** Insert encrypted password here ***</password>
                                      <id>snapshots</id>
                                    </server>
                                  </servers>
                                  <profiles>
                                    <profile>
                                      <repositories>
                                        <repository>
                                          <snapshots>
                                            <enabled>false</enabled>
                                          </snapshots>
                                          <id>central</id>
                                          <name>a-libs-release</name>
                                          <url>http://localhost:8081/artifactory/a-libs-release</url>
                                        </repository>
                                        <repository>
                                          <snapshots />
                                          <id>snapshots</id>
                                          <name>a-libs-snapshot</name>
                                          <url>http://localhost:8081/artifactory/a-libs-snapshot</url>
                                        </repository>
                                      </repositories>
                                      <pluginRepositories>
                                        <pluginRepository>
                                          <snapshots>
                                            <enabled>false</enabled>
                                          </snapshots>
                                          <id>central</id>
                                          <name>a-libs-release</name>
                                          <url>http://localhost:8081/artifactory/a-libs-release</url>
                                        </pluginRepository>
                                        <pluginRepository>
                                          <snapshots />
                                          <id>snapshots</id>
                                          <name>a-libs-snapshot</name>
                                          <url>http://localhost:8081/artifactory/a-libs-snapshot</url>
                                        </pluginRepository>
                                      </pluginRepositories>
                                      <id>artifactory</id>
                                    </profile>
                                  </profiles>
                                  <activeProfiles>
                                    <activeProfile>artifactory</activeProfile>
                                  </activeProfiles>
                                </settings>
                                    \s
                                ""\";
                """;
        when(filteredResourcesWebAddon.filterResource(any(), any(), any())).thenReturn(modelAfterFilter);

        ArtifactoryRestResponse restResponse = new ArtifactoryRestResponse<>();
        getIvySettingSnippetService.execute(request, restResponse);

        assertThat(restResponse.getIModel()).isInstanceOf(IvySettingModel.class);
        String ivySnippetResult = ((IvySettingModel) restResponse.getIModel()).getIvySnippet();
        assertThat(ivySnippetResult.contains(READABLE_ARTIFACTORY_USER_NAME_TEMPLATE)).isFalse();
        assertThat(ivySnippetResult.contains(READABLE_ARTIFACTORY_ESCAPED_PASSWORD_TEMPLATE)).isFalse();
        assertThat(ivySnippetResult.contains(TEMPLATE_ESCAPED_CREDENTIALS)).isFalse();
        assertThat(ivySnippetResult.contains(TEMPLATE_NAME)).isFalse();
    }
    @Test
    void verifyCredentialsAreNotAddedDuringDismiss() {
        prepareIvyUpdateSettingModel();

        when(request.getImodel()).thenReturn(new IvySettingModel());

        ArtifactoryRestResponse restResponse = new ArtifactoryRestResponse<>();
        getIvySettingSnippetService.execute(request, restResponse);

        assertThat(restResponse.getIModel()).isInstanceOf(IvySettingModel.class);
        String ivySnippetResult = ((IvySettingModel) restResponse.getIModel()).getIvySnippet();
        assertThat(ivySnippetResult.contains(READABLE_ARTIFACTORY_USER_NAME_TEMPLATE)).isTrue();
        assertThat(ivySnippetResult.contains(READABLE_ARTIFACTORY_PASSWORD_TEMPLATE)).isTrue();
        assertThat(ivySnippetResult.contains(TEMPLATE_ESCAPED_CREDENTIALS)).isFalse();
        assertThat(ivySnippetResult.contains(TEMPLATE_NAME)).isFalse();
    }

    private void prepareIvyUpdateSettingModel() {
        when(request.getQueryParamByKey("downloadScript")).thenReturn("false");
        when(request.getQueryParamByKey("deploy")).thenReturn("false");

        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getContextPath()).thenReturn("");
        when(servletRequest.getHeader(ArtifactoryRequest.JFROG_OVERRIDE_BASE_URL)).thenReturn(BASE_URL);

        when(request.getServletRequest()).thenReturn(servletRequest);
        String ivySnippet = """
                <?xml version="1.0" encoding="UTF-8"?>
                <ivy-settings>
                  <settings defaultResolver="main" />
                  <!--Authentication required for publishing (deployment).
                  'Artifactory Realm' is the realm used by Artifactory so don't change it.-->
                  <credentials host="localhost" realm="Artifactory Realm" username="${security.getCurrentUsername()}"
                  passwd="${security.getEncryptedPassword()!"*** Insert encrypted password here ***"}" />
                  <resolvers>
                    <chain name="main">
                      <ibiblio name="public" m2compatible="true" root="http://localhost:8081/artifactory/a-ivy-dev" />
                    </chain>
                  </resolvers>
                </ivy-settings>
                     
                """;
        doReturn(ivySnippet).when(getIvySettingSnippetService)
                .generateSettings(anyString(), any(), any());
        mockBaseUrlFromConfig();
        when(sysConfig.helper()).thenReturn(sysConfigHelper);
        when(sysConfigHelper.getRouterExternalPort()).thenReturn(-1);
    }
}
