package org.artifactory.storage.db.aql.itest.service;

import com.google.common.collect.Lists;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.artifactory.aql.model.AqlDomainEnum;
import org.artifactory.aql.model.AqlFieldEnum;
import org.artifactory.aql.model.AqlRepoProvider;
import org.artifactory.aql.model.DomainSensitiveField;
import org.artifactory.aql.result.AqlRestResult;
import org.artifactory.descriptor.repo.RemoteRepoDescriptor;
import org.artifactory.storage.db.aql.service.AqlPermissionProviderImpl;
import org.artifactory.storage.db.aql.service.AqlQueryStreamService;
import org.artifactory.storage.db.aql.service.AqlServiceImpl;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Ignore;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertTrue;

/**
 * @author Tomer Slutsky
 */
@Ignore
public class AqlJsonMultiStreamerTest extends DbBaseTest {
    String RESULT_SET_REPO = "repo-resultset";

    @Mock
    AqlRepoProvider providerMock;

    @Mock
    AqlPermissionProviderImpl permissionMock;

    @Mock
    CloseableHttpResponse responseMock;

    @Mock
    CloseableHttpResponse responseMock2;

    private AutoCloseable mockitoClosable;

    @Autowired
    private AqlServiceImpl aqlService;

    @Autowired
    private AqlQueryStreamService aqlQueryStreamService;

    @BeforeClass
    public void setup() {
        importSql("/sql/streamer.sql");
    }

    @BeforeMethod
    public void before() throws IOException, SQLException {
        this.mockitoClosable = MockitoAnnotations.openMocks(this);
        this.initDefaultRepoProviderMock();
        this.initDefaultCloseableHttpResponseMock();
        ReflectionTestUtils.setField(aqlService, "repoProvider", providerMock);
        ReflectionTestUtils.setField(aqlService, "permissionProvider", permissionMock);
        ReflectionTestUtils.setField(aqlQueryStreamService, "repoProvider", providerMock);
        ReflectionTestUtils.setField(aqlQueryStreamService, "permissionProvider", permissionMock);
        ReflectionTestUtils.setField(aqlService, "streamService", aqlQueryStreamService);
    }

    @AfterMethod
    public void after() throws Exception {
        this.mockitoClosable.close();
    }

    @Test
    public void streamerSingleResponseFromLocalAndRemoteTest() throws IOException {
        // Non default config
        when(providerMock.virtualRepoContainsTransitiveValidRepos("repo-remote")).thenReturn(false);
        when(providerMock.remoteRepoActualNameByKey("repo-remote")).thenReturn("repo1-local");
        when(responseMock.getEntity().getContent()).thenReturn(this.getSingleResponseMock());

        // PermissionProvider mock
        when(permissionMock.canRead(any())).thenReturn(true).thenReturn(true);

        try (AqlRestResult restResult = aqlService
                .executeQueryLazy("items.find({\"repo\": \"repo-remote\"}).transitive()")) {

            List<String> responses = new ArrayList<>();
            byte[] read = restResult.read();
            while (read != null) {
                responses.add(new String(read));
                read = restResult.read();
            }
            assertTrue(responses.get(0).contains("result"));
            assertTrue(responses.get(1).contains("\"repo\" : \"repo-remote-cache\""));
            assertTrue(responses.get(2).contains("\"repo\" : \"repo-remote\""));
            assertTrue(responses.get(3).contains("range"));
            assertTrue(responses.get(3).contains("\"total\" : 2"));
            assertTrue(responses.stream().noneMatch(curr -> curr.contains("\"repo\" : \"repo-local\"")));
        }
    }

    @Test
    public void streamerSingleResponseFromLocalAndEmptyRemoteTest() throws IOException {
        // Non default config
        when(providerMock.virtualRepoContainsTransitiveValidRepos(RESULT_SET_REPO)).thenReturn(false);
        when(providerMock.remoteRepoActualNameByKey(RESULT_SET_REPO)).thenReturn("repo1-local");
        when(responseMock.getEntity().getContent()).thenReturn(this.emptyResponseMock());

        // PermissionProvider mock
        when(permissionMock.canRead(any())).thenReturn(true).thenReturn(true);

        try (AqlRestResult restResult = aqlService
                .executeQueryLazy("items.find({\"repo\": \"" + RESULT_SET_REPO + "\"}).transitive()")) {

            List<String> responses = new ArrayList<>();
            byte[] read = restResult.read();
            while (read != null) {
                responses.add(new String(read));
                read = restResult.read();
            }
            assertTrue(responses.get(0).contains("result"));
            assertTrue(responses.get(1).contains("\"repo\" : \"" + RESULT_SET_REPO + "\""));
            assertTrue(responses.get(2).contains("range"));
            assertTrue(responses.get(2).contains("\"total\" : 1"));
            assertTrue(responses.stream().noneMatch(curr -> curr.contains("\"repo\" : \"repo-local\"")));
        }
    }

    @Test
    public void streamerSingleResponseFromLocalAndSkipFirstRemoteTest() throws IOException {
        // Non default config
        when(providerMock.virtualRepoContainsTransitiveValidRepos("repo-remote")).thenReturn(false);
        when(providerMock.remoteRepoActualNameByKey("repo-remote")).thenReturn("repo1-local");
        when(responseMock.getEntity().getContent()).thenReturn(this.getTwoResponsesMock());

        // PermissionProvider mock
        when(permissionMock.canRead(any())).thenReturn(true).thenReturn(false).thenReturn(true);

        try (AqlRestResult restResult = aqlService
                .executeQueryLazy("items.find({\"repo\": \"repo-remote\"}).transitive()")) {
            List<String> responses = new ArrayList<>();
            byte[] read = restResult.read();
            while (read != null) {
                responses.add(new String(read));
                read = restResult.read();
            }
            assertTrue(responses.get(0).contains("result"));
            assertTrue(responses.get(1).contains("\"repo\" : \"repo-remote-cache\""));
            assertTrue(responses.get(2).contains("\"repo\" : \"repo-remote\""));
            assertTrue(responses.get(2).contains("\"name\" : \"noskip\""));
            assertTrue(responses.stream().noneMatch(curr -> curr.contains("\"name\" : \"skip\"")));
            assertTrue(responses.get(3).contains("range"));
            assertTrue(responses.get(3).contains("\"total\" : 2"));
            assertTrue(responses.stream().noneMatch(curr -> curr.contains("\"repo\" : \"repo-local\"")));
        }
    }

    @Test
    public void streamerSingleResponseRemoteHasErrorTest() throws IOException {
        // Non default config
        when(providerMock.virtualRepoContainsTransitiveValidRepos("repo-remote")).thenReturn(false);
        when(providerMock.remoteRepoActualNameByKey("repo-remote")).thenReturn("repo1-local");
        when(responseMock.getEntity().getContent()).thenReturn(this.getSingleResponseMock());
        when(responseMock.getStatusLine()).thenReturn(Mockito.mock(StatusLine.class));
        when(responseMock.getStatusLine().getStatusCode()).thenReturn(403);

        // PermissionProvider mock
        when(permissionMock.canRead(any())).thenReturn(true).thenReturn(true);

        try (AqlRestResult restResult = aqlService
                .executeQueryLazy("items.find({\"repo\": \"repo-remote\"}).transitive()")) {
            List<String> responses = new ArrayList<>();
            byte[] read = restResult.read();
            while (read != null) {
                responses.add(new String(read));
                read = restResult.read();
            }
            assertTrue(responses.get(0).contains("result"));
            assertTrue(responses.get(1).contains("\"repo\" : \"repo-remote-cache\""));
            assertTrue(responses.stream().noneMatch(curr -> curr.equals("repo-remote")));
            assertTrue(responses.get(2).contains("range"));
            assertTrue(responses.get(2).contains("\"total\" : 1"));
        }
    }

    @Test
    public void streamerSingleResponseFromLocalAndRemoteLimitTest() throws IOException {
        // Non default config
        when(providerMock.virtualRepoContainsTransitiveValidRepos("repo-remote")).thenReturn(false);
        when(providerMock.remoteRepoActualNameByKey("repo-remote")).thenReturn("repo1-local");
        when(responseMock.getEntity().getContent()).thenReturn(this.getTwoResponsesMock());

        // PermissionProvider mock
        when(permissionMock.canRead(any())).thenReturn(true).thenReturn(true).thenReturn(true);

        try (AqlRestResult restResult =
                     aqlService.executeQueryLazy("items.find({\"repo\": \"repo-remote\"}).transitive().limit(2)")) {
            List<String> responses = new ArrayList<>();
            byte[] read = restResult.read();
            while (read != null) {
                responses.add(new String(read));
                read = restResult.read();
            }
            assertTrue(responses.get(0).contains("result"));
            assertTrue(responses.get(1).contains("\"repo\" : \"repo-remote-cache\""));
            assertTrue(responses.get(2).contains("\"repo\" : \"repo-remote\""));
            assertTrue(responses.get(2).contains("\"name\" : \"skip\""));
            assertTrue(responses.stream().noneMatch(curr -> curr.contains("\"name\" : \"notskip\"")));
            assertTrue(responses.get(3).contains("range"));
            assertTrue(responses.get(3).contains("\"total\" : 2"));
            assertTrue(responses.get(3).contains("\"end_pos\" : 2"));
            assertTrue(responses.stream().noneMatch(curr -> curr.contains("\"repo\" : \"repo-local\"")));
        }
    }

    @Test
    public void streamerVirtualRepoWithLocalAndRemoteTest() throws IOException {
        // Non default config
        when(providerMock.getRemoteRepoDescriptor("repo-remote")).thenReturn(null);
        when(providerMock.virtualRepoContainsTransitiveValidRepos("repo-virtual")).thenReturn(true);
        when(providerMock.getTransitiveRepoKeysFromVirtual("repo-virtual")).thenReturn(List.of("repo-remote"));
        when(providerMock.existsVirtualRepoKey("repo-virtual")).thenReturn(true);
        when(providerMock.getVirtualResolvedLocalFederatedAndCacheRepoKeys("repo-virtual"))
                .thenReturn(List.of(RESULT_SET_REPO));
        when(providerMock.remoteRepoActualNameByKey("repo-remote")).thenReturn("repo1-local");
        when(responseMock.getEntity().getContent()).thenReturn(this.getTwoResponsesMock());

        // PermissionProvider mock
        when(permissionMock.canRead(any())).thenReturn(true).thenReturn(true).thenReturn(true);

        try (AqlRestResult restResult =
                     aqlService.executeQueryLazy("items.find({\"repo\": \"repo-virtual\"}).transitive()")) {
            List<String> responses = new ArrayList<>();
            byte[] read = restResult.read();
            while (read != null) {
                responses.add(new String(read));
                read = restResult.read();
            }
            assertTrue(responses.get(0).contains("result"));
            assertTrue(responses.get(1).contains("\"repo\" : \"" + RESULT_SET_REPO + "\""));
            assertTrue(responses.get(2).contains("\"repo\" : \"repo-remote\""));
            assertTrue(responses.get(3).contains("\"repo\" : \"repo-remote\""));
            assertTrue(responses.get(2).contains("\"name\" : \"skip\""));
            assertTrue(responses.get(3).contains("\"name\" : \"noskip\""));
            assertTrue(responses.get(4).contains("range"));
            assertTrue(responses.get(4).contains("\"total\" : 3"));
            assertTrue(responses.stream().noneMatch(curr -> curr.contains("\"repo\" : \"repo-local\"")));
        }
    }

    @Test
    public void streamerVirtualRepoWithLocalAndMultiRemoteTest() throws IOException {
        // Non default config
        when(providerMock.getRemoteRepoDescriptor("repo-remote")).thenReturn(null);
        when(providerMock.virtualRepoContainsTransitiveValidRepos("repo-virtual")).thenReturn(true);
        when(providerMock.getTransitiveRepoKeysFromVirtual("repo-virtual"))
                .thenReturn(List.of("repo-remote", "repo-remote2"));
        when(providerMock.remoteRepoActualNameByKey("repo-remote")).thenReturn("repo-local");
        when(providerMock.remoteRepoActualNameByKey("repo-remote2")).thenReturn("repo1-local");
        when(providerMock.existsVirtualRepoKey("repo-virtual")).thenReturn(true);
        when(providerMock.getVirtualResolvedLocalFederatedAndCacheRepoKeys("repo-virtual"))
                .thenReturn(List.of(RESULT_SET_REPO));
        when(providerMock.executeAqlRemotely(any(), any())).thenReturn(responseMock).thenReturn(responseMock2);
        when(responseMock.getEntity().getContent()).thenReturn(this.getSingleResponseMock());
        when(responseMock2.getEntity().getContent()).thenReturn(this.getTwoResponsesMock());

        // PermissionProvider mock
        when(permissionMock.canRead(any())).thenReturn(true).thenReturn(true).thenReturn(true).thenReturn(true);

        try (AqlRestResult restResult =
                     aqlService.executeQueryLazy("items.find({\"repo\": \"repo-virtual\"}).transitive()")) {
            List<String> responses = new ArrayList<>();
            byte[] read = restResult.read();
            while (read != null) {
                responses.add(new String(read));
                read = restResult.read();
            }
            assertTrue(responses.get(0).contains("result"));
            assertTrue(responses.stream().anyMatch(curr -> curr.contains("\"repo\" : \"" + RESULT_SET_REPO + "\"")));
            assertTrue(responses.stream().anyMatch(curr -> curr.contains("\"repo\" : \"repo-remote\"")));
            assertTrue(responses.stream().anyMatch(curr -> curr.contains("\"name\" : \"skip\"")));
            assertTrue(responses.stream().anyMatch(curr -> curr.contains("\"name\" : \"noskip\"")));
            assertTrue(responses.get(5).contains("range"));
            assertTrue(responses.get(5).contains("\"total\" : 4"));
            assertTrue(responses.stream().noneMatch(curr -> curr.contains("\"repo\" : \"repo-local\"")));
            assertTrue(responses.stream().noneMatch(curr -> curr.contains("\"repo\" : \"repo1-local\"")));
        }
    }

    @Test
    public void streamerVirtualRepoWithLocalAndMultiRemoteWithOfflineTest() throws IOException {
        // Non default config
        when(providerMock.getRemoteRepoDescriptor("repo-remote")).thenReturn(null);
        when(providerMock.virtualRepoContainsTransitiveValidRepos("repo-virtual")).thenReturn(true);
        when(providerMock.isRemoteRepoOffline("repo-remote2")).thenReturn(true);
        when(providerMock.getTransitiveRepoKeysFromVirtual("repo-virtual"))
                .thenReturn(List.of("repo-remote", "repo-remote2").stream().sorted().collect(Collectors.toList()));
        when(providerMock.existsVirtualRepoKey("repo-virtual")).thenReturn(true);
        when(providerMock.getVirtualResolvedLocalFederatedAndCacheRepoKeys("repo-virtual"))
                .thenReturn(List.of(RESULT_SET_REPO));
        when(providerMock.remoteRepoActualNameByKey("repo-remote")).thenReturn("repo-local");
        when(providerMock.remoteRepoActualNameByKey("repo-remote2")).thenReturn("repo1-local");
        when(providerMock.executeAqlRemotely(any(), any())).thenReturn(responseMock).thenReturn(responseMock);
        when(responseMock.getEntity().getContent()).thenReturn(this.getSingleResponseMock())
                .thenReturn(this.getTwoResponsesMock());

        // PermissionProvider mock
        when(permissionMock.canRead(any())).thenReturn(true).thenReturn(true).thenReturn(true).thenReturn(true);

        try (AqlRestResult restResult =
                     aqlService.executeQueryLazy("items.find({\"repo\": \"repo-virtual\"}).transitive()")) {
            List<String> responses = new ArrayList<>();
            byte[] read = restResult.read();
            while (read != null) {
                responses.add(new String(read));
                read = restResult.read();
            }
            assertTrue(responses.get(0).contains("result"));
            assertTrue(responses.get(1).contains("\"repo\" : \"" + RESULT_SET_REPO + "\""));
            assertTrue(responses.get(2).contains("\"repo\" : \"repo-remote\""));
            assertTrue(responses.get(3).contains("range"));
            assertTrue(responses.get(3).contains("\"total\" : 2"));
            assertTrue(responses.stream().noneMatch(curr -> curr.contains("\"repo\" : \"repo-local\"")));
            assertTrue(responses.stream().noneMatch(curr -> curr.contains("\"repo\" : \"repo1-local\"")));
            assertTrue(responses.stream().noneMatch(curr -> curr.contains("\"repo\" : \"repo-remote2\"")));
        }
    }

    private void initDefaultRepoProviderMock() {
        // RepoProvider mock
        when(providerMock.getRemoteRepoDescriptor("repo-remote")).thenReturn(Mockito.mock(RemoteRepoDescriptor.class));
        when(providerMock.isRemoteRepoOffline("repo-remote")).thenReturn(false);
        when(providerMock.isRepoTransitiveValid("repo-remote")).thenReturn(true);
        when(providerMock.executeAqlRemotely(any(), any())).thenReturn(responseMock);

    }

    private void initDefaultCloseableHttpResponseMock() {
        when(responseMock.getEntity()).thenReturn(Mockito.mock(HttpEntity.class));
        when(responseMock.getStatusLine()).thenReturn(Mockito.mock(StatusLine.class));
        when(responseMock.getStatusLine().getStatusCode()).thenReturn(200);
        when(responseMock2.getEntity()).thenReturn(Mockito.mock(HttpEntity.class));
        when(responseMock2.getStatusLine()).thenReturn(Mockito.mock(StatusLine.class));
        when(responseMock2.getStatusLine().getStatusCode()).thenReturn(200);
    }

    private List<DomainSensitiveField> fields() {
        AqlDomainEnum domain = AqlDomainEnum.items;
        List<DomainSensitiveField> fields = new ArrayList<>();
        for (AqlFieldEnum field : domain.getDefaultResultFields()) {
            fields.add(new DomainSensitiveField(field, Lists.newArrayList(domain)));
        }

        return fields;
    }

    private InputStream emptyResponseMock() {
        String s = "{\n" +
                "    \"results\": [\n" +
                "    ],\n" +
                "    \"range\": {\n" +
                "        \"start_pos\": 0,\n" +
                "        \"end_pos\": 0,\n" +
                "        \"total\": 0\n" +
                "    }\n" +
                "}";
        return new ByteArrayInputStream(s.getBytes());
    }

    private InputStream getSingleResponseMock() {
        String s = "{\n" +
                "    \"results\": [\n" +
                "        {\n" +
                "            \"repo\": \"repo-local\",\n" +
                "            \"path\": \"hello-world/latest\",\n" +
                "            \"name\": \"manifest.json\",\n" +
                "            \"type\": \"file\",\n" +
                "            \"size\": 525,\n" +
                "            \"created\": \"2021-01-27T11:47:15.234+02:00\",\n" +
                "            \"created_by\": \"admin\",\n" +
                "            \"modified\": \"2021-01-27T11:37:51.000+02:00\",\n" +
                "            \"modified_by\": \"admin\",\n" +
                "            \"updated\": \"2021-01-27T11:47:15.235+02:00\"\n" +
                "        }\n" +
                "    ],\n" +
                "    \"range\": {\n" +
                "        \"start_pos\": 0,\n" +
                "        \"end_pos\": 9,\n" +
                "        \"total\": 9\n" +
                "    }\n" +
                "}";
        return new ByteArrayInputStream(s.getBytes());
    }

    private InputStream getTwoResponsesMock() {
        String s = "{\n" +
                "    \"results\": [\n" +
                "        {\n" +
                "            \"repo\": \"repo1-local\",\n" +
                "            \"path\": \"hello-world/latest\",\n" +
                "            \"name\": \"skip\",\n" +
                "            \"type\": \"file\",\n" +
                "            \"size\": 525,\n" +
                "            \"created\": \"2021-01-27T11:47:15.234+02:00\",\n" +
                "            \"created_by\": \"admin\",\n" +
                "            \"modified\": \"2021-01-27T11:37:51.000+02:00\",\n" +
                "            \"modified_by\": \"admin\",\n" +
                "            \"updated\": \"2021-01-27T11:47:15.235+02:00\"\n" +
                "        },\n" +
                "        {\n" +
                "            \"repo\": \"repo1-local\",\n" +
                "            \"path\": \"hello-world/latest\",\n" +
                "            \"name\": \"noskip\",\n" +
                "            \"type\": \"file\",\n" +
                "            \"size\": 6,\n" +
                "            \"created\": \"2021-01-27T11:47:15.309+02:00\",\n" +
                "            \"created_by\": \"admin\",\n" +
                "            \"modified\": \"2021-01-27T11:47:15.307+02:00\",\n" +
                "            \"modified_by\": \"admin\",\n" +
                "            \"updated\": \"2021-01-27T11:47:15.309+02:00\"\n" +
                "        }],\n" +
                "    \"range\": {\n" +
                "        \"start_pos\": 0,\n" +
                "        \"end_pos\": 9,\n" +
                "        \"total\": 9\n" +
                "    }\n" +
                "}";

        return new ByteArrayInputStream(s.getBytes());
    }
}