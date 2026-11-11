package org.artifactory.util;

import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.descriptor.config.CentralConfigDescriptorImpl;
import org.artifactory.repo.ArtifactoryFederationServiceException;
import org.artifactory.repo.federated.FederatedMember;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;

import java.util.List;
import java.util.stream.Stream;

import static org.artifactory.repo.federated.FederatedMember.BASE_URL_PLACEHOLDER;
import static org.artifactory.util.FederatedMemberUtils.isLocal;
import static org.artifactory.util.FederatedMemberUtils.resolveUrl;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Tamar Rosen
 */
class FederatedMemberUtilsTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private ArtifactoryContext context;
    private AutoCloseable autoCloseable;

    @BeforeEach
    void setup() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterMethod
    private void cleanup() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        autoCloseable.close();
    }

    @ParameterizedTest
    @MethodSource("resolveUrlProvider")
    void testResolveUrl(String memberUrl, String baseUrl, String fedBaseUrl, String expectedResolvedUrl) {
        // given
        when(context.isReady()).thenReturn(true);
        CentralConfigService configService = mock(CentralConfigService.class);
        CentralConfigDescriptorImpl configDescriptor = new CentralConfigDescriptorImpl();
        configDescriptor.setUrlBase(baseUrl);
        configDescriptor.setFederatedRepoUrlBase(fedBaseUrl);
        when(configService.getDescriptor()).thenReturn(configDescriptor);
        when(context.getCentralConfig()).thenReturn(configService);
        FederatedMember member = new FederatedMember();
        member.setUrl(memberUrl);
        // when
        String resolvedUrl = resolveUrl(member);
        // then
        assertEquals(expectedResolvedUrl, resolvedUrl);
    }

    @ParameterizedTest
    @MethodSource("isLocalUrlProvider")
    void testIsLocal(String memberUrl, String key, String baseUrl, boolean expected) {
        FederatedMember member = new FederatedMember();
        member.setUrl(memberUrl);
        assertEquals(expected, isLocal(member, key, baseUrl));
    }

    public static Stream<Arguments> resolveUrlProvider() {
        String BASE_URL = "https://baseurl.com";
        String FED_BASE_URL = "https://fed-baseurl.com";
        return Stream.of(
                Arguments.of(BASE_URL_PLACEHOLDER + "/repo", null, FED_BASE_URL + "/",
                        FED_BASE_URL + "/artifactory/repo"),
                Arguments.of(BASE_URL_PLACEHOLDER + "/repo", null, FED_BASE_URL, FED_BASE_URL + "/artifactory/repo"),
                Arguments.of(BASE_URL_PLACEHOLDER + "/repo", BASE_URL, null, BASE_URL + "/artifactory/repo"),
                Arguments.of(BASE_URL_PLACEHOLDER + "/repo", BASE_URL, FED_BASE_URL,
                        FED_BASE_URL + "/artifactory/repo"),
                Arguments.of(BASE_URL_PLACEHOLDER + "/repo", BASE_URL, FED_BASE_URL,
                        FED_BASE_URL + "/artifactory/repo"),
                Arguments.of(BASE_URL_PLACEHOLDER + "/repo", BASE_URL, FED_BASE_URL + "/artifactory",
                        FED_BASE_URL + "/artifactory/repo"),
                Arguments.of(BASE_URL + "/repo", null, null, BASE_URL + "/repo")
        );
    }

    public static Stream<Arguments> isLocalUrlProvider() {
        String BASE_URL = "https://baseurl.com";
        return Stream.of(
                Arguments.of(BASE_URL_PLACEHOLDER + "/repo", "repo", BASE_URL, true),
                Arguments.of(BASE_URL + "/repo", "repo", BASE_URL, true),
                Arguments.of("https://remote-url.com/repo", "repo", BASE_URL, false),
                Arguments.of(BASE_URL_PLACEHOLDER + "/repo", "other-repo", BASE_URL, false)
        );
    }

    @ParameterizedTest
    @MethodSource("isSelfMemberProvider")
    void testIsSelfMember(String memberUrl, boolean expected) {
        FederatedMember member = new FederatedMember();
        member.setUrlOrTemplate(memberUrl);
        assertEquals(expected, FederatedMemberUtils.isSelfMember(member));
    }

    public static Stream<Arguments> isSelfMemberProvider() {
        return Stream.of(
                Arguments.of(BASE_URL_PLACEHOLDER + "/repo", true),                     // self template -> true
                Arguments.of(BASE_URL_PLACEHOLDER + "/other-repo", true),               // any key, still self -> true
                Arguments.of("https://remote-url.com/artifactory/repo", false),         // concrete remote url -> false
                Arguments.of("https://host/" + BASE_URL_PLACEHOLDER + "/repo",
                        false),  // placeholder not leading -> false
                Arguments.of(null, false)                                               // no url -> false
        );
    }

    /**
     * Documents why {@link FederatedMemberUtils#isSelfMember} uses {@code startsWith} rather than {@code contains}:
     * for a URL where the placeholder is present but not the leading segment, {@code contains} would classify it as
     * a self member while {@code startsWith} (the domain rule, matching FederationConfig#isFederated) does not.
     * Real member URLs never take this shape, so the two agree in practice — this test only pins the semantics.
     */
    @Test
    void isSelfMemberUsesPrefixNotContains() {
        String url = "https://host/" + BASE_URL_PLACEHOLDER + "/repo";
        FederatedMember member = new FederatedMember();
        member.setUrlOrTemplate(url);

        assertTrue(url.contains(BASE_URL_PLACEHOLDER), "precondition: contains() would match this url");
        assertFalse(FederatedMemberUtils.isSelfMember(member),
                "placeholder not at the start must NOT be treated as self (startsWith, not contains)");
    }

    @Test
    void testCreateSelfMember() {
        FederatedMember self = FederatedMemberUtils.createSelfMember("my-repo");

        assertEquals(BASE_URL_PLACEHOLDER + "/my-repo", self.getUrlOrTemplate());
        assertTrue(self.isEnabled());
        assertTrue(FederatedMemberUtils.isSelfMember(self), "a created self member must be detected as self");
    }

    @ParameterizedTest
    @MethodSource("membersWithSelfProvider")
    void testMembersWithSelfMember(List<FederatedMember> input, int expectedSize) {
        List<FederatedMember> result = FederatedMemberUtils.membersWithSelfMember(input, "A");

        assertEquals(expectedSize, result.size());
        assertTrue(result.stream().anyMatch(FederatedMemberUtils::isSelfMember),
                "result must always contain a self member");
    }

    public static Stream<Arguments> membersWithSelfProvider() {
        FederatedMember self = FederatedMemberUtils.createSelfMember("A");
        FederatedMember remote = new FederatedMember();
        remote.setUrlOrTemplate("https://other.jfrog.io/artifactory/A");
        return Stream.of(
                Arguments.of(List.of(), 1),                 // empty -> self added -> 1
                Arguments.of(List.of(self), 1),             // self only -> not duplicated -> 1
                Arguments.of(List.of(self, remote), 2),     // self + remote -> 2
                Arguments.of(List.of(remote), 2)            // remote only -> self added -> 2
        );
    }

    @Test
    void testGetLocalMember() throws Exception {
        // Given
        String sourceRepoKey = "test-repo";
        String baseUrl = "https://baseurl.com";
        
        CentralConfigService centralConfigService = mock(CentralConfigService.class);
        CentralConfigDescriptorImpl configDescriptor = new CentralConfigDescriptorImpl();
        configDescriptor.setFederatedRepoUrlBase(baseUrl);
        when(centralConfigService.getDescriptor()).thenReturn(configDescriptor);

        FederatedMember localMember = new FederatedMember();
        localMember.setUrl(baseUrl + "/artifactory/" + sourceRepoKey);
        
        List<FederatedMember> federatedMembers = List.of(localMember);
        
        // When
        FederatedMember result =
                FederatedMemberUtils.getLocalMember(sourceRepoKey, federatedMembers, centralConfigService);
        
        // Then
        assertEquals(localMember, result);
    }
    
    @Test
    void testGetLocalMemberWhenNoMemberExists() {
        // Given
        String sourceRepoKey = "test-repo";
        String baseUrl = "https://baseurl.com";
        
        CentralConfigService centralConfigService = mock(CentralConfigService.class);
        CentralConfigDescriptorImpl configDescriptor = new CentralConfigDescriptorImpl();
        configDescriptor.setFederatedRepoUrlBase(baseUrl);
        when(centralConfigService.getDescriptor()).thenReturn(configDescriptor);

        FederatedMember remoteMember = new FederatedMember();
        remoteMember.setUrl("https://remote-url.com/artifactory/" + sourceRepoKey);
        
        List<FederatedMember> federatedMembers = List.of(remoteMember);
        
        // When & Then
        assertThrows(ArtifactoryFederationServiceException.class,
                () -> FederatedMemberUtils.getLocalMember(sourceRepoKey, federatedMembers, centralConfigService));
    }
}