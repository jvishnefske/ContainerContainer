package com.jfrog.build.resource;

import com.jfrog.build.model.api.BuildInfo;
import com.jfrog.build.model.api.Builds;
import com.jfrog.build.model.api.BuildsByName;
import com.jfrog.build.acl.model.BuildPatternArtifactsRequestWithRepo;
import com.jfrog.build.model.api.PromotionResult;
import com.jfrog.build.command.BuildAddModulesCommand;
import com.jfrog.build.command.BuildCreateCommand;
import com.jfrog.build.command.BuildDeleteCommand;
import com.jfrog.build.command.BuildGetByNameCommand;
import com.jfrog.build.command.BuildGetAllCommand;
import com.jfrog.build.command.BuildGetInfoCommand;
import com.jfrog.build.command.BuildGetArtifactsByPatternsCommand;
import com.jfrog.build.command.BuildPromoteCommand;
import com.jfrog.build.command.BuildRetainCommand;
import com.jfrog.build.command.BuildRedirectUploadCommand;
import com.jfrog.build.command.BuildRenameCommand;
import com.jfrog.build.model.api.BuildsDeletionModel;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.Response;
import org.jfrog.common.StringList;
import org.jfrog.build.api.Build;
import org.jfrog.build.api.BuildRetention;
import org.jfrog.build.api.Module;
import org.jfrog.build.api.dependency.BuildPatternArtifacts;
import org.jfrog.build.api.release.Promotion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_NO_CONTENT;
import static org.apache.http.HttpStatus.SC_OK;
import static org.jfrog.storage.binstore.ifc.model.BinaryElementHeaders.CHECKSUM_SHA256;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuildPublicResourceTest {

    private BuildPublicResource resource;

    private BuildAddModulesCommand addModuleCommand;
    private BuildCreateCommand createCommand;
    private BuildDeleteCommand deleteCommand;
    private BuildGetAllCommand getAllCommand;
    private BuildGetArtifactsByPatternsCommand getArtifactsByPatternsCommand;
    private BuildGetByNameCommand getByNameCommand;
    private BuildGetInfoCommand getInfoCommand;
    private BuildPromoteCommand promoteCommand;
    private BuildRedirectUploadCommand redirectUploadCommand;
    private BuildRenameCommand renameBuildCommand;
    private BuildRetainCommand retainCommand;

    @BeforeEach
    void setUp() {
        addModuleCommand = mock(BuildAddModulesCommand.class);
        createCommand = mock(BuildCreateCommand.class);
        deleteCommand = mock(BuildDeleteCommand.class);
        getAllCommand = mock(BuildGetAllCommand.class);
        getArtifactsByPatternsCommand = mock(BuildGetArtifactsByPatternsCommand.class);
        getByNameCommand = mock(BuildGetByNameCommand.class);
        getInfoCommand = mock(BuildGetInfoCommand.class);
        promoteCommand = mock(BuildPromoteCommand.class);
        redirectUploadCommand = mock(BuildRedirectUploadCommand.class);
        renameBuildCommand = mock(BuildRenameCommand.class);
        retainCommand = mock(BuildRetainCommand.class);
        resource = new BuildPublicResource(
                addModuleCommand,
                createCommand,
                deleteCommand,
                getAllCommand,
                getArtifactsByPatternsCommand,
                getByNameCommand,
                getInfoCommand,
                promoteCommand,
                redirectUploadCommand,
                renameBuildCommand,
                retainCommand
        );
    }

    @Test
    void createBuild_returnsNoContentWithSha256Header() {
        Build build = new Build();
        build.setName("name");
        build.setNumber("1");
        when(createCommand.createBuild(any(), any(), any())).thenReturn("manifest-sha256");

        Response response = resource.createBuild(build, "my-repo", "my-project");

        assertEquals(SC_NO_CONTENT, response.getStatus());
        assertNotNull(response.getHeaderString(CHECKSUM_SHA256.getHeaderName()));
        assertEquals("manifest-sha256", response.getHeaderString(CHECKSUM_SHA256.getHeaderName()));
        verify(createCommand).createBuild(same(build), eq("my-repo"), eq("my-project"));
    }

    @Test
    void createBuild_passesNullRepoAndProjectToCommand() {
        Build build = new Build();
        when(createCommand.createBuild(any(), isNull(), isNull())).thenReturn("sha");

        resource.createBuild(build, null, null);

        verify(createCommand).createBuild(same(build), isNull(), isNull());
    }

    @Test
    void getBuilds_returnsOkWithBuildsEntity() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        Builds builds = new Builds();
        builds.slf = "https://host/artifactory/api/build?buildRepo=artifactory-build-info";
        when(getAllCommand.getAllBuilds(same(request), eq("my-repo"), eq("my-project"))).thenReturn(builds);

        Response response = resource.getBuilds(request, "my-repo", "my-project");

        assertEquals(SC_OK, response.getStatus());
        assertEquals(builds, response.getEntity());
        verify(getAllCommand).getAllBuilds(same(request), eq("my-repo"), eq("my-project"));
    }

    @Test
    void getBuilds_passesNullRepoAndProjectToCommand() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        Builds builds = new Builds();
        when(getAllCommand.getAllBuilds(same(request), isNull(), isNull())).thenReturn(builds);

        Response response = resource.getBuilds(request, null, null);

        assertEquals(SC_OK, response.getStatus());
        verify(getAllCommand).getAllBuilds(same(request), isNull(), isNull());
    }

    @Test
    void getBuilds_returnsOkWithBuildsByNameByNameEntity() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        BuildsByName byName = new BuildsByName();
        byName.slf = "https://host/api/build/my-app?buildRepo=artifactory-build-info";
        when(getByNameCommand.getBuildsByName(same(request), eq("my-app"), eq("repo"), eq("proj")))
                .thenReturn(byName);

        Response response = resource.getBuildsByName(request, "my-app", "repo", "proj");

        assertEquals(SC_OK, response.getStatus());
        assertEquals(byName, response.getEntity());
        verify(getByNameCommand).getBuildsByName(same(request), eq("my-app"), eq("repo"), eq("proj"));
    }

    @Test
    void getBuilds_ByName_passesNullRepoAndProjectToCommand() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        BuildsByName byName = new BuildsByName();
        when(getByNameCommand.getBuildsByName(same(request), eq("bn"), isNull(), isNull()))
                .thenReturn(byName);

        resource.getBuildsByName(request, "bn", null, null);

        verify(getByNameCommand).getBuildsByName(same(request), eq("bn"), isNull(), isNull());
    }

    @Test
    void getBuildInfo_delegatesToCommand() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        BuildInfo entity = new BuildInfo();
        when(getInfoCommand.getBuildInfo(
                same(request),
                eq("a"),
                eq("1"),
                eq("started"),
                eq("br"),
                eq("pk"),
                eq("0"),
                eq(true))).thenReturn(entity);

        Response response = resource.getBuildInfo(request, "a", "1", "started", "br", "pk", "0", true);

        assertEquals(SC_OK, response.getStatus());
        assertEquals(entity, response.getEntity());
        verify(getInfoCommand).getBuildInfo(
                same(request), eq("a"), eq("1"), eq("started"), eq("br"), eq("pk"), eq("0"), eq(true));
    }

    @Test
    void addModule_callsCommandAndReturnsNoContent() {
        Module module = new Module();

        Response response = resource.addModule(List.of(module), "a", "1", "started", "br", "pk");

        assertEquals(SC_NO_CONTENT, response.getStatus());
        verify(addModuleCommand).addModules(eq(List.of(module)), eq("a"), eq("1"), eq("started"), eq("br"), eq("pk"));
    }

    @Test
    void getArtifacts_ByPatterns_delegatesToCommand() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        BuildPatternArtifactsRequestWithRepo patternRequest =
                new BuildPatternArtifactsRequestWithRepo("some-build-name", "LATEST", "br", "pk");
        BuildPatternArtifacts artifact = mock(BuildPatternArtifacts.class);
        when(getArtifactsByPatternsCommand.getArtifactsByPatterns(same(request), eq(List.of(patternRequest))))
                .thenReturn(List.of(artifact));

        Response response = resource.getArtifactsByPatterns(request, List.of(patternRequest));

        assertEquals(SC_OK, response.getStatus());
        assertEquals(List.of(artifact), response.getEntity());
        verify(getArtifactsByPatternsCommand).getArtifactsByPatterns(same(request), eq(List.of(patternRequest)));
    }

    @Test
    void renameBuilds_delegatesToCommandAndReturnsOkWithMessage() {
        HttpServletRequest request = mock(HttpServletRequest.class);

        Response response = resource.renameBuilds(request, "from-name", "to-name", "my-repo", "my-project");

        assertEquals(SC_OK, response.getStatus());
        assertEquals(
                "Build renaming of 'from-name' to 'to-name' was successfully started",
                response.getEntity());
        verify(renameBuildCommand).renameBuilds(
                same(request), eq("from-name"), eq("to-name"), eq("my-repo"), eq("my-project"));
    }

    @Test
    void redirectBuildUpload_delegatesToCommand() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        resource.redirectBuildUpload(request, response, "api/build/some-repo/path");

        verify(redirectUploadCommand).redirectBuildUpload(
                same(request), same(response), eq("api/build/some-repo/path"));
    }

    @Test
    void promote_returnsOkWithResult() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        Promotion promotion = mock(Promotion.class);
        PromotionResult result = mock(PromotionResult.class);
        when(promotion.isFailFast()).thenReturn(true);
        when(result.errorsOrWarningHaveOccurred()).thenReturn(false);
        when(promoteCommand.promote(same(request), same(promotion), eq("bn"), eq("1"), eq("br"), eq("pk")))
                .thenReturn(result);

        Response response = resource.promote(promotion, request, "bn", "1", "br", "pk");

        assertEquals(SC_OK, response.getStatus());
        assertEquals(result, response.getEntity());
        verify(promoteCommand).promote(same(request), same(promotion), eq("bn"), eq("1"), eq("br"), eq("pk"));
    }

    @Test
    void promote_returnsBadRequestWhenFailFastAndErrorsOccurred() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        Promotion promotion = mock(Promotion.class);
        PromotionResult result = mock(PromotionResult.class);
        when(promotion.isFailFast()).thenReturn(true);
        when(result.errorsOrWarningHaveOccurred()).thenReturn(true);
        when(promoteCommand.promote(same(request), same(promotion), eq("bn"), eq("1"), isNull(), isNull()))
                .thenReturn(result);

        Response response = resource.promote(promotion, request, "bn", "1", null, null);

        assertEquals(SC_BAD_REQUEST, response.getStatus());
        assertEquals(result, response.getEntity());
        verify(promoteCommand).promote(same(request), same(promotion), eq("bn"), eq("1"), isNull(), isNull());
    }

    @Test
    void retainBuilds_returnsBadRequestWhenMissingOrZeroCount() {
        Response nullRetention = resource.retainBuilds(null, "my-build", "my-repo", "my-project", false);
        assertEquals(400, nullRetention.getStatus());
        assertEquals("Max count retention needs to be a positive number", nullRetention.getEntity());

        BuildRetention retention = mock(BuildRetention.class);
        when(retention.getCount()).thenReturn(0);

        Response zeroCount = resource.retainBuilds(retention, "my-build", "my-repo", "my-project", false);
        assertEquals(400, zeroCount.getStatus());
        assertEquals("Max count retention needs to be a positive number", zeroCount.getEntity());
    }

    @Test
    void retainBuilds_delegatesToCommandAndReturnsNoContent() {
        BuildRetention retention = mock(BuildRetention.class);
        when(retention.getCount()).thenReturn(5);

        Response response = resource.retainBuilds(retention, "my-build", "my-repo", "my-project", true);

        assertEquals(SC_NO_CONTENT, response.getStatus());
        verify(retainCommand).retainBuilds(retention, "my-build", "my-repo", "my-project", true);
    }

    @Test
    void deleteBuilds_returnsOkWithMessageFromCommand() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        StringList buildNumbers = new StringList("1,2");
        when(deleteCommand.deleteBuilds(
                same(request),
                eq("some-build-name"),
                eq("artifactory-build-info"),
                eq("phoenix"),
                eq(buildNumbers),
                eq(true),
                eq(false))).thenReturn("delete-message");

        Response response = resource.deleteBuilds(
                request, "some-build-name", "artifactory-build-info", "phoenix", buildNumbers, 1, 0);

        assertEquals(SC_OK, response.getStatus());
        assertEquals("delete-message", response.getEntity());
        verify(deleteCommand).deleteBuilds(
                same(request),
                eq("some-build-name"),
                eq("artifactory-build-info"),
                eq("phoenix"),
                eq(buildNumbers),
                eq(true),
                eq(false));
    }

    @Test
    void deleteBuilds_passesNullsAndFalseFlagsByDefault() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(deleteCommand.deleteBuilds(
                same(request), eq("some-build-name"), isNull(), isNull(), isNull(), eq(false), eq(false)))
                .thenReturn("delete-message");

        Response response = resource.deleteBuilds(request, "some-build-name", null, null, null, 0, 0);

        assertEquals(SC_OK, response.getStatus());
        assertEquals("delete-message", response.getEntity());
        verify(deleteCommand).deleteBuilds(
                same(request), eq("some-build-name"), isNull(), isNull(), isNull(), eq(false), eq(false));
    }

    @Test
    void deleteBuilds_deleteAllFlagIsTrueWhenQueryParamIsOne() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(deleteCommand.deleteBuilds(
                same(request), eq("some-build-name"), isNull(), isNull(), isNull(), eq(false), eq(true)))
                .thenReturn("delete-message");

        Response response = resource.deleteBuilds(request, "some-build-name", null, null, null, 0, 1);

        assertEquals(SC_OK, response.getStatus());
        verify(deleteCommand).deleteBuilds(
                same(request), eq("some-build-name"), isNull(), isNull(), isNull(), eq(false), eq(true));
    }

    @Test
    void bulkDeleteBuilds_delegatesModelFieldsToCommand() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        BuildsDeletionModel model = new BuildsDeletionModel();
        model.setBuildName("some-build-name");
        model.setBuildRepo("artifactory-build-info");
        model.setProject("phoenix");
        model.setBuildNumbers(List.of("1", "2"));
        model.setDeleteArtifacts(true);
        model.setDeleteAll(false);
        when(deleteCommand.deleteBuilds(
                same(request),
                eq("some-build-name"),
                eq("artifactory-build-info"),
                eq("phoenix"),
                eq(List.of("1", "2")),
                eq(true),
                eq(false))).thenReturn("bulk-delete-message");

        Response response = resource.bulkDeleteBuilds(request, model);

        assertEquals(SC_OK, response.getStatus());
        assertEquals("bulk-delete-message", response.getEntity());
        verify(deleteCommand).deleteBuilds(
                same(request),
                eq("some-build-name"),
                eq("artifactory-build-info"),
                eq("phoenix"),
                eq(List.of("1", "2")),
                eq(true),
                eq(false));
    }

    @Test
    void bulkDeleteBuilds_deleteAllFlagPropagatesToCommand() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        BuildsDeletionModel model = new BuildsDeletionModel();
        model.setBuildName("some-build-name");
        model.setDeleteAll(true);
        when(deleteCommand.deleteBuilds(
                same(request), eq("some-build-name"), isNull(), isNull(), isNull(), eq(false), eq(true)))
                .thenReturn("bulk-delete-message");

        Response response = resource.bulkDeleteBuilds(request, model);

        assertEquals(SC_OK, response.getStatus());
        verify(deleteCommand).deleteBuilds(
                same(request), eq("some-build-name"), isNull(), isNull(), isNull(), eq(false), eq(true));
    }
}
