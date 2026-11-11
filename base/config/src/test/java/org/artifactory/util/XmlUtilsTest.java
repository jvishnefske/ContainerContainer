package org.artifactory.util;

import org.artifactory.exception.DocumentBuildException;
import org.jdom2.Document;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author Volodymyr Shulga
 * @author Shahaf Golan
 * @author Natalie Berni
 */
class XmlUtilsTest {
    private static final String XML_CONTENT = """
            <?xml version="1.0" encoding="UTF-8"?>
            <settings
            xsi:schemaLocation=
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
                      <name>maven-virtual</name>
                      <url>http://localhost:8081/artifactory/maven-virtual</url>
                    </repository>
                    <repository>
                      <snapshots />
                      <id>snapshots</id>
                      <name>maven-virtual</name>
                      <url>http://localhost:8081/artifactory/maven-virtual</url>
                    </repository>
                  </repositories>
                  <pluginRepositories>
                    <pluginRepository>
                      <snapshots>
                        <enabled>false</enabled>
                      </snapshots>
                      <id>central</id>
                      <name>maven-virtual</name>
                      <url>http://localhost:8081/artifactory/maven-virtual</url>
                    </pluginRepository>
                    <pluginRepository>
                      <snapshots />
                      <id>snapshots</id>
                      <name>maven-virtual</name>
                      <url>http://localhost:8081/artifactory/maven-virtual</url>
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

    @Test
    void shouldParseSuccessfully() {
        Document document = XmlUtils
                .parse(XmlUtilsTest.class.getResourceAsStream("/config/test/config.2.2.3.with.docker.repos.xml"));
        Assertions.assertNotNull(document);
    }

    @Test
    void shouldFailParseForDoctype() throws IOException {

        String config = Files.readString(
                Path.of(
                        XmlUtilsTest.class.getResource("/config/test/config.3.0.2_doctype.xml").getPath()
                )
        );
        Assertions.assertNotNull(config);
        Assertions.assertThrows(DocumentBuildException.class, () -> {
            XmlUtils.parseStrict(config);
        }, "Error on line 2: DOCTYPE is disallowed when the feature " +
                "\\\"http://apache.org/xml/features/disallow-doctype-decl\\\" set to true.");
    }

    @Test
    void shouldProcessDoctypeWithoutStrictOption() {
        Document document = XmlUtils.parse(
                XmlUtilsTest.class.getResourceAsStream("/config/test/config.3.0.2_doctype.xml")
        );
        Assertions.assertNotNull(document);
    }

    @Test
    void testMavenSettingsXmlStructureChanged() throws Exception {
        Reader reader = new StringReader(XML_CONTENT);
        Reader newXmlReaderContent =
                XmlUtils.overrideSettingConfiguration(reader, XmlUtils.canParseXml(reader),
                        "${security.getEscapedEncryptedPassword()!\"*** Insert encrypted password here ***\"}");

        StringWriter stringWriter = new StringWriter();
        BufferedReader bufferedReader = new BufferedReader(newXmlReaderContent);
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            stringWriter.write(line + "\n");
        }

        String content = stringWriter.toString();

        Assertions.assertTrue(content.contains("<name>Authorization</name>"));
    }
    @Test
    void testMavenSettingsXmlStructureDidNotChange() throws Exception {
        String xmlContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                 <settings
                 xsi:schemaLocation=
                 "http://maven.apache.org/SETTINGS/1.2.0 http://maven.apache.org/xsd/settings-1.2.0.xsd"
                 xmlns="http://maven.apache.org/SETTINGS/1.2.0"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                <server>
                <username>${security.getCurrentUsername()}</username>
                <password>${security.getEscapedEncryptedPassword()!"*** Insert encrypted password here ***"}</password>
                <id>central</id>
                </server>
                   <profiles>
                     <profile>
                     </profile>
                   </profiles>
                   <activeProfiles>
                     <activeProfile>artifactory</activeProfile>
                   </activeProfiles>
                 </settings>
                """;

        Reader reader = new StringReader(xmlContent);
        Reader newXmlReaderContent =
                XmlUtils.overrideSettingConfiguration(reader, XmlUtils.canParseXml(reader),
                        "${security.getEscapedEncryptedPassword()!\"*** Insert encrypted password here ***\"}");

        StringWriter stringWriter = new StringWriter();
        BufferedReader bufferedReader = new BufferedReader(newXmlReaderContent);
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            stringWriter.write(line + "\n");
        }

        String content = stringWriter.toString();

        Assertions.assertFalse(content.contains("<name>Authorization</name>"));
        Assertions.assertFalse(content.contains("<server>"));
    }

    @Test
    void testMavenSettingsXmlStructureFreeMarker() throws Exception {
        String xmlContent = """
                            <?xml version="1.0" encoding="UTF-8"?>
                            <settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
                                      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                      xsi:schemaLocation=
                               "http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd">
                                <#if !security.currentUser().isTransientUser()>
                                    <#assign userName = security.getCurrentUsername()?remove_beginning("token:")/>
                                    <#if userName == "anonymous">
                                        <#assign userName = "LOGIN_AND_DOWNLOAD_AGAIN"/>
                                        <#assign password = "READ_THE_COMMENT_ABOVE">
                                    <#else>
                                        <#if request.getHeaderValues("Authorization")?has_content>
                                      <#if request.getHeaderValues("Authorization").nextElement()?starts_with("Bearer")>
                                      <#assign password =
                                    request.getHeaderValues("Authorization").nextElement()?remove_beginning("Bearer ")/>
                                            <#else>
                                          <#assign password = security.getEncryptedPassword()!"READ_THE_COMMENT_ABOVE">
                                            </#if>
                                        </#if>
                                    </#if>
                                <#else>
                                    <#assign userName = security.getCurrentUsername()?remove_beginning("token:")/>
                                    <#assign password =
                                    request.getHeaderValues("Authorization").nextElement()?remove_beginning("Bearer ")/>
                                </#if>
                                <interactiveMode>true</interactiveMode>
                                <servers>
                                    <server>
                                        <id>central</id>
                                        <username>${userName}</username>
                                        <password>${password}</password>
                                    </server>
                                    <#if password != "READ_THE_COMMENT_ABOVE">
                                    <server>
                                        <id>example.com</id>
                                        <username>${userName}</username>
                                        <password>${password}</password>
                                    </server>
                                    <server>
                                        <id>staging.example.com</id>
                                        <username>${userName}</username>
                                        <password>${password}</password>
                                    </server>
                                    </#if>
                                </servers>
                            </settings>
                """;

        Reader reader = new StringReader(xmlContent);

        Assertions.assertNull(XmlUtils.canParseXml(reader));

        StringWriter stringWriter = new StringWriter();
        BufferedReader bufferedReader = new BufferedReader(reader);
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            stringWriter.write(line + "\n");
        }

        String content = stringWriter.toString();

        Assertions.assertFalse(content.contains("<name>Authorization</name>"));
        Assertions.assertFalse(content.contains("<server>"));
    }
}
