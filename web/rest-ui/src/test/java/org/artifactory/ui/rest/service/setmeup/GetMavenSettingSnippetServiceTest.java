package org.artifactory.ui.rest.service.setmeup;

import jakarta.servlet.http.HttpServletRequest;
import org.artifactory.api.maven.MavenService;
import org.artifactory.api.maven.MavenSettings;
import org.artifactory.api.maven.MavenSettingsServer;
import org.artifactory.api.security.SecurityService;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.common.service.StreamRestResponse;
import org.artifactory.ui.rest.model.setmeup.MavenSettingModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import static org.artifactory.ui.rest.service.setmeup.GetSettingSnippetService.READABLE_ARTIFACTORY_ESCAPED_PASSWORD_TEMPLATE;
import static org.artifactory.ui.rest.service.setmeup.GetSettingSnippetService.READABLE_ARTIFACTORY_USER_NAME_TEMPLATE;
import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetMavenSettingSnippetServiceTest extends GetSettingSnippetServiceTest {

    private GetMavenSettingSnippetService getMavenSettingSnippetService;

    @Mock
    private SecurityService securityService;
    @Mock
    private MavenService mavenService;

    @BeforeEach
    @Override
    void setUp() {
        super.setUp();
        getMavenSettingSnippetService = Mockito.spy(new GetMavenSettingSnippetService());
        getMavenSettingSnippetService.setSecurityService(securityService);
        getMavenSettingSnippetService.setMavenService(mavenService);
    }

    @Test
    void verifyThatServerIsAddedWhenMirroringIsEnabled() {
        String mirrorId = "jf-libs-mirror";

        MavenSettingModel model = createBaseMavenSettingsWithoutMirror();
        model.setMirror(mirrorId);

        MavenSettings mavenSettingModel =
                getMavenSettingSnippetService.assembleLocalSettingsModel(ARTIFACTORY_URL, model);

        assertThat(mavenSettingModel.getServers()).isNotNull().hasSize(3);
        assertThat(mavenSettingModel.getServers().get(2).getId()).isEqualTo(mirrorId);

        //mirror with MIRROR_ID id is expected in result:
        assertThat(mavenSettingModel.getMirrorRepositories()).isNotNull().hasSize(1);
        assertThat(mavenSettingModel.getMirrorRepositories().get(0).getId()).isEqualTo(mirrorId);
    }


    @Test
    void verifyThatServerIsNotAddedWhenMirroringIsDisabled() {
        MavenSettingModel model = createBaseMavenSettingsWithoutMirror();

        MavenSettings mavenSettingModel =
                getMavenSettingSnippetService.
                        assembleLocalSettingsModel(ARTIFACTORY_URL, model);

        //no servers with mirror id are expected
        assertThat(mavenSettingModel.getServers()).isNotNull().hasSize(2);
        for (MavenSettingsServer server : mavenSettingModel.getServers()) {
            assertThat(server.getId()).isNotEqualTo("jf-libs-mirror");
        }
        //no mirrors are expected
        assertThat(mavenSettingModel.getMirrorRepositories()).isNotNull().hasSize(0);
    }

    @Test
    void testExecuteDownloadScript() {
        when(request.getQueryParamByKey("downloadScript")).thenReturn("true");
        when(request.getQueryParamByKey("deploy")).thenReturn("false");

        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getContextPath()).thenReturn("");
        when(servletRequest.getHeader(ArtifactoryRequest.JFROG_OVERRIDE_BASE_URL)).thenReturn(BASE_URL);

        when(request.getServletRequest()).thenReturn(servletRequest);
        doReturn("http://localhost:8081/").when(getMavenSettingSnippetService)
                .generateSettings(anyString(), any(), any());
        mockBaseUrlFromConfig();
        when(sysConfig.helper()).thenReturn(sysConfigHelper);
        when(sysConfigHelper.getRouterExternalPort()).thenReturn(-1);

        when(request.getImodel()).thenReturn(new MavenSettingModel());
        StreamRestResponse restResponse = new StreamRestResponse();
        getMavenSettingSnippetService.execute(request, restResponse);
        assertThat(restResponse.getEntity()).isInstanceOf(String.class);
    }

    @Test
    void verifyCredentialsAreWhenGeneratingAToken() throws Exception {
        prepareMavenUpdateSettingModel();

        MavenSettingModel model = new MavenSettingModel();
        model.setPassword("mytoken");
        when(request.getImodel()).thenReturn(model);
        String modelAfterFilter = """
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
                """;
        when(filteredResourcesWebAddon.filterResource(any(), any(), any())).thenReturn(modelAfterFilter);

        ArtifactoryRestResponse restResponse = new ArtifactoryRestResponse<>();
        getMavenSettingSnippetService.execute(request, restResponse);

        assertThat(restResponse.getIModel()).isInstanceOf(MavenSettingModel.class);
        String mavenSnippetResult = ((MavenSettingModel) restResponse.getIModel()).getMavenSnippet();
        assertThat(mavenSnippetResult.contains(READABLE_ARTIFACTORY_USER_NAME_TEMPLATE)).isFalse();
        assertThat(mavenSnippetResult.contains(READABLE_ARTIFACTORY_ESCAPED_PASSWORD_TEMPLATE)).isFalse();
        assertThat(mavenSnippetResult.contains(TEMPLATE_ESCAPED_CREDENTIALS)).isFalse();
        assertThat(mavenSnippetResult.contains(TEMPLATE_NAME)).isFalse();
    }

    @Test
    void verifyCredentialsAreNotAddedDuringDismiss() {
        prepareMavenUpdateSettingModel();

        when(request.getImodel()).thenReturn(new MavenSettingModel());

        ArtifactoryRestResponse restResponse = new ArtifactoryRestResponse<>();
        getMavenSettingSnippetService.execute(request, restResponse);

        assertThat(restResponse.getIModel()).isInstanceOf(MavenSettingModel.class);
        String mavenSnippetResult = ((MavenSettingModel) restResponse.getIModel()).getMavenSnippet();
        assertThat(mavenSnippetResult.contains(READABLE_ARTIFACTORY_USER_NAME_TEMPLATE)).isTrue();
        assertThat(mavenSnippetResult.contains(READABLE_ARTIFACTORY_ESCAPED_PASSWORD_TEMPLATE)).isTrue();
        assertThat(mavenSnippetResult.contains(TEMPLATE_ESCAPED_CREDENTIALS)).isFalse();
        assertThat(mavenSnippetResult.contains(TEMPLATE_NAME)).isFalse();
    }

    private void prepareMavenUpdateSettingModel() {
        when(request.getQueryParamByKey("downloadScript")).thenReturn("false");
        when(request.getQueryParamByKey("deploy")).thenReturn("false");

        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getContextPath()).thenReturn("");
        when(servletRequest.getHeader(ArtifactoryRequest.JFROG_OVERRIDE_BASE_URL)).thenReturn(BASE_URL);

        when(request.getServletRequest()).thenReturn(servletRequest);
        String mavenSnippet = """
                <?xml version="1.0" encoding="UTF-8"?>
                <settings xsi:schemaLocation=
                "http://maven.apache.org/SETTINGS/1.2.0 http://maven.apache.org/xsd/settings-1.2.0.xsd"
                xmlns="http://maven.apache.org/SETTINGS/1.2.0"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                  <servers>
                    <server>
                 <username>${security.getCurrentUsername()}</username>
                 <password>${security.getEscapedEncryptedPassword()!"*** Insert encrypted password here ***"}</password>
                      <id>central</id>
                    </server>
                    <server>
                 <username>${security.getCurrentUsername()}</username>
                 <password>${security.getEscapedEncryptedPassword()!"*** Insert encrypted password here ***"}</password>
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
                     
                """;
        doReturn(mavenSnippet).when(getMavenSettingSnippetService)
                .generateSettings(anyString(), any(), any());
        mockBaseUrlFromConfig();
        when(sysConfig.helper()).thenReturn(sysConfigHelper);
        when(sysConfigHelper.getRouterExternalPort()).thenReturn(-1);
    }

    @Test
    void verifySnippetIsNotCorruptedForAnonymousUser() {
        when(securityService.isAnonymous()).thenReturn(true);
        when(securityService.isAnonAccessEnabled()).thenReturn(true);
        when(filteredResourcesWebAddon.getGeneratedSettingsUserCredentialsTemplate(false)).thenReturn("");
        when(filteredResourcesWebAddon.getGeneratedSettingsUserCredentialsTemplate(true)).thenReturn("");

        when(request.getQueryParamByKey("downloadScript")).thenReturn("false");
        when(request.getQueryParamByKey("deploy")).thenReturn("false");

        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getContextPath()).thenReturn("");
        when(servletRequest.getHeader(ArtifactoryRequest.JFROG_OVERRIDE_BASE_URL)).thenReturn(BASE_URL);
        when(request.getServletRequest()).thenReturn(servletRequest);

        String anonymousSnippet = """
                <?xml version="1.0" encoding="UTF-8"?>
                <settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                  <profiles>
                    <profile>
                      <repositories>
                        <repository>
                          <id>central</id>
                          <url>http://localhost:8081/artifactory/libs-release</url>
                        </repository>
                      </repositories>
                      <id>artifactory</id>
                    </profile>
                  </profiles>
                </settings>
                """;
        doReturn(anonymousSnippet).when(getMavenSettingSnippetService)
                .generateSettings(anyString(), any(), any());
        mockBaseUrlFromConfig();
        when(sysConfig.helper()).thenReturn(sysConfigHelper);
        when(sysConfigHelper.getRouterExternalPort()).thenReturn(-1);
        when(request.getImodel()).thenReturn(new MavenSettingModel());

        ArtifactoryRestResponse restResponse = new ArtifactoryRestResponse<>();
        getMavenSettingSnippetService.execute(request, restResponse);

        String mavenSnippetResult = ((MavenSettingModel) restResponse.getIModel()).getMavenSnippet();
        assertThat(mavenSnippetResult.contains("${ARTIFACTORY_PASSWORD}")).isFalse();
        assertThat(mavenSnippetResult.contains(READABLE_ARTIFACTORY_ESCAPED_PASSWORD_TEMPLATE)).isFalse();
        assertThat(mavenSnippetResult).isEqualTo(anonymousSnippet);
    }

    @Test
    void verifyThatServerIsNotAddedWhenAuthorizationIsAsynchronous() {
        when(securityService.isAnonymous()).thenReturn(true);
        when(securityService.isAnonAccessEnabled()).thenReturn(true);

        String mirrorId = "jf-libs-mirror";
        MavenSettingModel model = createBaseMavenSettingsWithoutMirror();
        model.setMirror(mirrorId);

        MavenSettings mavenSettingModel =
                getMavenSettingSnippetService.assembleLocalSettingsModel(ARTIFACTORY_URL, model);

        assertThat(mavenSettingModel.getServers()).isNotNull().hasSize(0);
        //mirror with MIRROR_ID id is expected in result:
        assertThat(mavenSettingModel.getMirrorRepositories()).isNotNull().hasSize(1);
        assertThat(mavenSettingModel.getMirrorRepositories().get(0).getId()).isEqualTo(mirrorId);
    }

    private MavenSettingModel createBaseMavenSettingsWithoutMirror() {
        MavenSettingModel model = new MavenSettingModel();
        model.setRelease("jf-libs-release");
        model.setSnapshot("jf-libs-snapshot");
        model.setPluginRelease("jf-libs-release");
        model.setPluginSnapshot("jf-libs-snapshot");
        return model;
    }
}