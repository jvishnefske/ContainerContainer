# Artifactory OSS fork: Groovy plugins and plugin-defined repository types

Base: JFrog Artifactory OSS 7.161.26 published sources (AGPL-3.0), sha256
`b867422f3137c542b402c38a2ca2584396ef431b691ca70f038ff5cc3768802c`, committed
pristine as the first git commit. Deployment target: the stock
`releases-docker.jfrog.io/jfrog/artifactory-oss:7.161.26` image with the
fork's `artifactory-*.jar` files overlaid into its `WEB-INF/lib`.

## Background

OSS ships only `PluginsAddonDefaultImpl`, a no-op stub. The Groovy plugin
engine, its DSL and the `papi` service implementations (`Repositories`,
`Searches`, `Security`) live in the closed Pro addon. `RepoType` is a closed
enum referenced in roughly 400 files, so it is not replaced. A plugin package
type is a `Generic` repository whose `packageTypeConfig` map carries the
reserved key `pluginPackageType`. `Generic` is already the fallback for unknown
types everywhere, and `packageTypeConfig` already persists, diffs and
replicates.

## Requirements

Each item is checked only when a named test demonstrates it.

### Build and deployment

- [x] B1. The fork builds the WAR from source with JDK 25 and Maven, resolving
      dependencies only from Maven Central and JFrog's public `jfrog-jars`
      repository.
      Shown by a full reactor `mvn install` in `maven:3.9-eclipse-temurin-25`
      with only those two repositories configured (all 34 modules build).
- [x] B2. The fork's jars, overlaid on the stock 7.161.26 image, start
      Artifactory with existing data and no schema migration.
      Evidence: `docker/it/run-it.sh` on 2026-11-11 against
      `releases-docker.jfrog.io/jfrog/artifactory-oss@sha256:d103c0464d33645acdcbfbb6fc61310d7faf14c9893975cbee562487eaeac1d1`
      (overlay built by `docker/build-overlay.sh`: 18 `artifactory-*-7.161.15.jar`
      replaced, `groovy-4.0.32.jar` added, the version the WAR resolves through
      the Spring Boot BOM). The stock image writes a repository and a file, then
      the overlay starts on the same volume: `B2.stock-data-written`,
      `B2.fork-starts-on-existing-data`, `B2.stock-data-readable`,
      `B2.no-schema-migration` (the only conversion logged is PRE_INIT
      7.161.15 to 7.161.15, which the stock image also logs). Both images use the
      embedded Derby database, which since 7.161 needs the stock opt-in
      `JF_SHARED_DATABASE_ALLOWNONPOSTGRESQL=true`.
- [x] B3. With no plugins present the fork behaves as stock OSS: the existing
      unit test suite of every changed module passes.
      Full suites on 2026-11-11: backend/core 10435 run 0 failed, web/rest 1205
      run 0 failed, web/rest-ui 3068 run 0 failed (3 skipped, as upstream).

### Groovy plugin engine

- [x] G1. On startup, every `*.groovy` file in `ArtifactoryHome.getPluginsDir()`
      (`$JFROG_HOME/artifactory/var/etc/artifactory/plugins`) is compiled and
      loaded. A plugin that fails to compile is reported in plugin status and
      does not stop startup or other plugins.
      Evidence (backend/core: `org.artifactory.addon.plugin.groovy.GroovyPluginEngineTest`):
      `#badScriptsAreReportedAndDoNotBlockOtherPlugins`,
      `#startupLoadsPluginsThroughContextReadiness`,
      `#missingPluginsDirectoryLoadsNothing`. Startup is exercised through
      `GroovyPluginsAddon.onContextReady()`, not a booted server.
- [x] G2. `POST /api/plugins/reload` reloads all plugins without a restart and
      replaces previously registered hooks and package types.
      Evidence: backend/core: `org.artifactory.addon.plugin.groovy.GroovyPluginEngineTest#reloadReplacesHooksAndPackageTypes`,
      `#pluginThatStopsCompilingLosesItsPackageTypes`,
      `#packageTypeMovedBetweenPluginsInOneReloadIsAccepted`; web/rest: `org.artifactory.rest.resource.plugin.GroovyPluginsResourceTest#reloadLoadsPluginsAndReportsEachFile`.
- [x] G3. The DSL supports `download { beforeDownloadRequest, altResponse,
      afterDownload }`, `storage { beforeCreate, afterCreate, beforeDelete,
      afterDelete }` and `executions { name { params -> } }`, bound to the
      existing `PluginAction` interfaces in `backend/core/.../addon/plugin`.
      Evidence (backend/core: `org.artifactory.addon.plugin.groovy.PluginDslBindingTest`):
      `#downloadBeforeDownloadRequestSetsModifiedPathOnDownloadCtx`,
      `#downloadAltResponseSetsStatusAndMessageOnResponseCtx`,
      `#downloadAfterDownloadReceivesTheResponse`,
      `#storageHooksFireForCreateAndDelete`,
      `#executionsBlockDeclaresANamedExecution`,
      `#hooksBindToTheCatalogActionTypes`. The `upload { beforeUploadRequest }`
      block and the other `PluginAction` hooks listed in `HookCatalog` are
      also bound.
- [x] G4. `POST /api/plugins/execute/{name}` runs a named execution, admin-only
      by default, and returns its status and message.
      Evidence: web/rest: `org.artifactory.rest.resource.plugin.GroovyPluginsResourceTest#executeRunsTheNamedExecutionAndReturnsItsStatusAndMessage`,
      `#executeIsAdminOnlyUnlessTheExecutionAllowsTheCaller`; backend/core: `org.artifactory.addon.plugin.groovy.PluginExecutionTest`
      (`#nonAdminIsRefusedByDefault`, `#declaredUserMayRun`,
      `#declaredGroupMemberMayRun`, `#cancelledExecutionReturnsTheCancelStatus`).
- [x] G5. `GET /api/plugins` lists loaded plugins and their hooks.
      Evidence: web/rest: `org.artifactory.rest.resource.plugin.GroovyPluginsResourceTest#listingShowsExecutionsAndPluginsWithHooks`;
      backend/core: `org.artifactory.addon.plugin.groovy.PluginListingTest#listsPluginsHooksExecutionsAndFailures`.
- [x] G6. Plugin scripts see `repositories`, `log` and `security` bindings.
      `repositories` supports at least `getFileInfo`, `getContent`, `deploy`,
      `delete`, `exists`, `getProperties` and `setProperty`.
      Evidence: backend/core: `org.artifactory.addon.plugin.groovy.PluginBindingsTest#scriptUsesEveryRepositoriesMethodAndSecurityAndLog`,
      `#bindingsMapHoldsTheThreeVariables`; `getChildren` (used by the example
      plugin) by `#getChildrenDelegatesToTheRepositoryService`. `security` exposes only
      `currentUsername`, `admin` and `anonymous`.
- [x] G7. An exception thrown inside a hook is logged with the plugin name and
      does not fail the request, except when the hook explicitly sets an error
      status (for example `CancelException` in `beforeCreate`).
      Evidence: backend/core: `org.artifactory.addon.plugin.groovy.GroovyPluginEngineTest#hookExceptionIsReportedWithPluginNameAndDoesNotFailTheCall`,
      `#cancelExceptionInBeforeCreateFailsTheOperation`;
      `PackageTypeDslTest#downloadHandlerFailureIsReportedAndDeclines`.

### Plugin-defined repository types

- [x] R1. A plugin declares `packageType('name') { displayName, layout,
      download {}, upload {}, index {} }`. Names are unique, lowercase
      `[a-z0-9-]+`, and must not collide with a `RepoType` value.
      Registry: invalid names, built-in collisions, duplicates within a
      batch and names owned by another plugin are rejected all or nothing
      (`PluginPackageTypeRegistryImplTest#invalidNameRejectsWholeBatch`,
      `#builtInNameCollisionRejected`, `#duplicateNameWithinBatchRejected`,
      `#nameOwnedByAnotherPluginRejected`). DSL: the block builds a
      `PluginPackageType` with its handlers and registry rejections surface in
      plugin status (`org.artifactory.addon.plugin.groovy.PackageTypeDslTest`,
      `#invalidTypeNameSurfacesInPluginStatus`).
- [x] R2. Creating a local Generic repository whose package type configuration
      sets `pluginPackageType` to `name` succeeds only when `name` is
      registered. Otherwise it returns 400 with a message naming the unknown type.
      In OSS the public repository API (`PUT /api/repositories/{key}`) is a Pro
      feature (`RestAddonDefaultImpl` throws `MissingRestAddonException`, 400),
      so the OSS create path is the YAML configuration API,
      `PATCH /api/system/configuration`, whose `localRepositories` entries of
      `type: generic` accept `pluginPackageType`
      (`RepoConfigMigrationPatchUtilsTest#testBuildLocalPackageTypeConfigGenericCarriesPluginPackageType`).
      Evidence: `docker/it/run-it.sh` (2026-11-11, image digest under B2):
      `R2.unknown-type-rejected-400-naming-it`, `R2.unknown-type-not-stored`,
      `R2.registered-type-accepted`. The save-time validator and the UI REST layer
      are covered by `PluginPackageTypeRepoConfigValidatorTest#createWithUnknownTypeRejectedWith400NamingType`,
      `#createWithRegisteredTypeAccepted` and
      `PluginPackageTypeRepoValidationTest#createWithUnknownTypeReturns400AndStoresNothing`.
- [x] R3. GET requests to a plugin-typed repository go through the type's
      `download` handler, which can serve stored content, a generated
      response (for example an index document) or a status code. When the
      handler declines, normal Generic handling applies.
      Evidence: `DownloadServiceImplPluginPackageTypeTest#handledMessageIsServedAsBody`,
      `#handledStreamIsServedAsBody`, `#handledErrorStatusIsSentAsError`,
      `#handledHeadSendsHeadResponse`, `#declinedFallsThroughToGeneric`,
      `#handlerExceptionFallsThroughToGeneric` and
      `#handledStreamWithoutSizeIsStreamedWithoutContentLength`. Live, with read
      permissions enforced before the handler: `docker/it/run-it.sh`
      `R5.R7.index-json-lists-a-1.0.tgz` (served by the handler) and
      `R3.anonymous-index-denied`.
- [x] R4. PUT requests to a plugin-typed repository go through the type's
      `upload` handler, which can reject the upload or rewrite the target path
      before storage.
      Evidence: `UploadServiceImplPluginPackageTypeTest#acceptLeavesRequestUnchanged`,
      `#rejectSendsStatusAndStoresNothing`, `#rewriteInSameRepoTranslatesTargetPath`
      and `#rewriteToOtherRepoRejectedWith400`. Live: `docker/it/run-it.sh`
      `R4.upload-tgz-201`, `R4.upload-txt-rejected-400`, `R4.rejected-file-not-stored`.
- [x] R5. Whenever the files of a local plugin-typed repository change, the
      type's `index` handler runs asynchronously after the change commits, so
      it can maintain metadata files such as an index. The events covered are
      a file deployed into the repository, copied into it or moved into it
      (the handler gets the destination path), and a file moved out of it or
      deleted from it, including each file of a deleted folder (the handler
      gets the removed path). A move within the repository reports both
      paths. Folder events and trash-can items are not reported, and the
      handler's own writes, moves and deletes in the repository do not
      trigger it again.
      Evidence: `PluginPackageTypeIndexInterceptorTest#indexRunsOnceAndNotForItsOwnWrites`,
      `#dispatchIsAsyncAfterCommit`, `#copyIntoPluginRepoIndexesDestination`,
      `#moveIntoPluginRepoIndexesDestination`,
      `#moveOutOfPluginRepoIndexesSourceWithRemovedPath`,
      `#moveWithinPluginRepoReportsRemovedAndNewPath`,
      `#deleteFromPluginRepoIndexesRemovedPath`,
      `#deleteThatIsPartOfAMoveIsLeftToTheMove`,
      `#folderCopyMoveDeleteAreIgnored`,
      `#handlersOwnMovesAndDeletesDoNotRetrigger` and `#affectedPathsDecision`. Live:
      `docker/it/run-it.sh` `R5.R7.index-json-lists-a-1.0.tgz`.
- [x] R6. If the plugin is removed, a repository of its type keeps working as
      plain Generic and its stored artifacts stay intact.
      Evidence: `DownloadServiceImplPluginPackageTypeTest#unregisteredTypeFallsThroughToGeneric`,
      `UploadServiceImplPluginPackageTypeTest#unregisteredTypeStoresAsGeneric`,
      `PluginPackageTypeIndexInterceptorTest#typeRemovedBeforeAsyncRunSkipsHandler`,
      `PluginPackageTypeResolverTest#unregisteredTypeServesAsGenericAndIsReportedOnce`,
      `PluginPackageTypeRepoConfigValidatorTest#importOfRepoWithUnknownTypeDoesNotFail`
      and `#updateKeepingTypeOfRemovedPluginAccepted`. Live: `docker/it/run-it.sh`
      `R6.reload-after-removal`, `R6.execution-gone`,
      `R6.stored-artifact-still-served`, `R6.plain-generic-upload-after-removal`.
- [x] R7. A worked example plugin ships under `plugins/examples/` and defines a
      simple package type with an index, covered by an integration test.
      `plugins/examples/simple-index.groovy` defines type `simpleidx` (accepts `.tgz`
      uploads and its own `index.json`, maintains and serves that index) and the
      admin-only POST execution
      `simpleidxReindex`. Evidence: `docker/it/run-it.sh` (2026-11-11, image
      digest under B2): `G5.plugins-lists-simpleidxReindex`,
      `G4.execute-wrong-method-405`, `R4.upload-tgz-201`,
      `R4.upload-txt-rejected-400`, `R5.R7.index-json-lists-a-1.0.tgz`,
      `R7.reindex-execution`; unit: `org.artifactory.addon.plugin.groovy.ExamplePluginTest`
      loads the shipped file.
- [x] R8. Editing a plugin-typed repository keeps its plugin package type
      unless the update itself sets or clears it. The UI repository editor
      has no field for the key and rebuilds `packageTypeConfig` from its
      model, so single-repository updates in `RepositoryConfigServiceImpl`
      carry the stored type over when the update does not mention the key; a
      blank or null value clears it. Bulk upserts and imports store the
      configuration as given.
      Evidence: `PluginPackageTypesTest#updateWithoutTheKeyKeepsStoredType`,
      `#updateSettingTheKeyWins`, `#updateWithBlankOrNullValueClearsTheKey`,
      `RepositoryConfigServiceImplTest#upsertWithoutPluginPackageTypeKeepsStoredType`,
      `#upsertChangingPluginPackageTypeIsHonoured`,
      `#upsertClearingPluginPackageTypeRemovesIt` and
      `FederationRoundTripTest#uiEditOmitsPluginPackageTypeAndBackendMergeKeepsIt`.

### Plugin HTTP API

A plugin package type can serve a complete HTTP API, so that a registry such
as OCI Distribution can be written purely as a plugin. The contract lives in
`backend/core/.../addon/plugin/pkgtype/` (`PluginApiHandler`,
`PluginApiRequest`, `PluginApiResponse`, `PluginApiMount`), the gate and
service in `pkgtype/impl/` (`PluginApiGate`, `PluginApiService`,
`PluginApiAuthenticationAdapter`), and the REST shell in
`web/rest/.../rest/resource/pkgapi/` (`DockerPluginApiResource`,
`PkgPluginApiResource`, `PluginApiEndpoint`).

- [x] A1. A type declares an optional `api` handler that takes an immutable
      request value and returns an immutable response value. The request has
      the upper-case method, repository key, the path after the key, decoded
      query parameters in order, headers keyed by lower-case name, the live
      body stream (empty when there is none) and its declared length (-1 when
      unknown), the caller's name, admin and anonymous flags, and a base URL.
      The response has a status of 100 to 599, headers and a body that is
      none, bytes or a stream with an optional size. Maps are unmodifiable
      copies, header names and values cannot carry line breaks, and the
      existing `PluginPackageType` constructor and the other handler types are
      unchanged.
      Evidence: `PluginApiContractTest#requestNormalisesMethodPathAndHeaderNamesAndCopiesMaps`,
      `#requestRejectsInvalidValues`, `#queryStringIsParsedInOrderAndDecoded`,
      `#readBodyIsBounded`, `#responseEnforcesStatusRangeAndImmutableHeaders`,
      `#responseBodies`, `#packageTypeApiIsOptionalAndTheOldConstructorStillWorks`.
- [x] A2. GET, HEAD, PUT, POST, PATCH and DELETE on
      `/api/docker/{repoKey}/{path}` and `/api/pkg/{repoKey}/{path}` reach the
      handler of the repository's type with the request as sent. The query
      string is parsed from the raw string, so no servlet parameter parsing can
      consume a body.
      Evidence (web/rest): `PluginApiResourceTest#everyMethodReachesTheHandlerWithItsRequest`,
      `#pkgPrefixServesTheSameApi`, `#resourcesDeclareEveryMethodOnTheirPaths`.
- [x] A3. Registry clients reach the API through `/v2/`. With a plugin type
      that has an API handler and no reverse proxy configured,
      `DockerInternalRewrite` uses the repository-path-prefix method, so
      `<host>/v2/<repoKey>/<rest>` is forwarded to
      `/api/docker/<repoKey>/v2/<rest>` for any host name (the stock default
      is the subdomain method, which maps dotted hosts to their first label
      and leaves `localhost` unrewritten). The ping `GET /v2/`, and `/v2`
      without the slash, answers 200 `{}` with
      `Docker-Distribution-API-Version: registry/2.0` to an authenticated
      caller, and 401 with `WWW-Authenticate: Basic realm="Artifactory Realm"`
      to an anonymous one unless anonymous access is enabled; without such a
      type, and for other single-segment paths the rewrite sends there (such
      as `/v2/_catalog`), it answers 404. Basic credentials authenticate
      requests under `/api/docker/` once such a type exists: stock
      Artifactory leaves them to a package-handler adapter that OSS lacks, so
      without `PluginApiAuthenticationAdapter` every registry client would
      stay anonymous.
      Evidence: web/application: `DockerInternalRewritePluginApiTest#pingWithAndWithoutTrailingSlashLandsOnTheDockerPing`,
      `#withoutReverseProxyConfigurationAPluginApiSwitchesToRepoPathPrefix`,
      `#stockSubdomainDefaultWouldMisrouteDottedHostsAndSkipLocalhost`;
      web/rest: `PluginApiResourceTest#pingAnswersRegistryClients`; backend/core:
      `PluginApiGateTest#pingDecision`,
      `PluginApiServiceTest#pingNeedsARegisteredApiTypeAndChallengesAnonymousUnlessAnonymousAccessIsOn`,
      `PluginApiAuthenticationAdapterTest#acceptanceRule`,
      `#onlyTheDockerEndpointUnderTheContextPathIsHandled`,
      `#withoutAnApiTypeOrOutsideTheEndpointNothingIsHandledAndSecurityIsNotConsulted`,
      `#dockerRequestWithoutBasicHeaderIsNotHandled`,
      `ArtifactoryPackageBeansConfigTest#testPackageAuthenticationAdapter`.
- [x] A4. The request's base URL lets a handler build absolute `Location`
      URLs as the client sees them. A request forwarded from
      `/v2/<repoKey>/...` gets the platform URL and `urlFor` maps
      `v2/<rest>` to `<platform>/v2/<repoKey>/<rest>`; a direct call gets the
      Artifactory URL and maps to the `/api/docker/` or `/api/pkg/` path. Both
      URLs come from Artifactory's own resolution (`X-JFrog-Override-Base-Url`,
      the configured base URL, trusted `X-Forwarded-Host` and
      `X-Forwarded-Proto`, then the request's `Host`).
      Evidence: `PluginApiContractTest#mountIsDetectedFromTheForwardedUri`,
      `#baseUrlAndUrlForGiveTheUrlTheClientUses`; web/rest:
      `PluginApiResourceTest#forwardedV2RequestGetsThePlatformBaseUrl`.
- [x] A5. Permissions are enforced in Java before any plugin code runs, by a
      pure decision function. GET and HEAD need read, PUT, POST and PATCH
      deploy, DELETE delete, each checked on the repository root as the
      caller. A repository that is not local, or whose registered type has no
      API handler, answers 404 without a permission check; an anonymous caller
      without the permission gets 401 with the Basic challenge, an
      authenticated one 403; a blacked-out repository answers 404 after the
      permission check. Any other method is refused with 405.
      Evidence: `PluginApiGateTest` (all tests),
      `PluginApiServiceTest#permissionsAreCheckedOnTheRepositoryRootAsTheCaller`,
      `#anonymousCallerWithoutPermissionIsChallenged`,
      `#remoteMissingAndNonApiRepositoriesAre404WithoutPermissionChecks`,
      `#blackedOutRepositoryIsRefused`; web/rest:
      `PluginApiResourceTest#repositoryWithoutApiTypeIs404AndRunsNoPluginCode`,
      `#anonymousWithoutPermissionGets401WithTheBasicChallengeAndAuthenticatedGets403`,
      `#blackedOutRepositoryIsRefused`.
- [x] A6. Bodies stream in both directions. The handler gets the servlet
      input stream itself, unread. A stream response is copied in 64 KiB
      pieces, sent with `Content-Length` when its size is known, and closed
      after copying, unread for HEAD, and in any case at the end of the
      request. HEAD sends headers only, and a handler-set `Content-Length` on
      a response without a body is kept so HEAD can advertise a size.
      Evidence (web/rest): `PluginApiResourceTest#largeBodiesAreStreamedInBothDirections`
      (64 MiB generated response, never held in memory),
      `#headSendsHeadersOnlyAndClosesTheStream`,
      `#emptyBodyGetsZeroLengthAndBytesGetTheirLength`; backend/core:
      `PackageTypeApiDslTest#streamBodiesAreHandedThroughUnread`.
- [x] A7. The API fails closed. A handler that throws or returns nothing is
      answered with a generic 500 that carries no failure details, and the
      failure is logged with the type and plugin name. In the DSL, a closure
      that throws or returns something that is not a valid response is
      reported in plugin status and answered with the same 500, and a stream
      carried by a rejected value is closed. `CancelException` answers with
      its status (500 when it is not 4xx or 5xx).
      Evidence: `PluginApiServiceTest#handlerExceptionAndNullBecomeAGeneric500`;
      web/rest: `PluginApiResourceTest#handlerFailureIsAGeneric500`; backend/core:
      `PackageTypeApiDslTest#failuresAndInvalidReturnsFailClosedWith500AndAreReported`,
      `#invalidReturnClosesTheStreamItCarried`, `#cancelExceptionAnswersWithItsStatus`.
- [x] A8. The DSL accepts `api` inside `packageType('name') { }`; the closure
      gets the request and returns a `PluginApiResponse` or a map of
      `status`, `headers` and `body`, where the body is absent, bytes, a
      string sent as UTF-8, a stream with an optional size, or a content
      handle from `repositories`. `PluginApiRequest` and `PluginApiResponse`
      are default imports of plugin scripts.
      Evidence: `PackageTypeApiDslTest#recordReturnIsUsedAsIs`,
      `#mapReturnIsConverted`,
      `#resourceStreamHandleBodyIsStreamedWithItsSizeAndClosedWithTheStream`,
      `#typeWithoutApiBlockHasNoApi`, `#conversionRejectsWhatIsNotAResponse`.
- [x] A9. The `repositories` binding offers what a registry needs with bounded
      memory: `findFile` (size, SHA-256, SHA-1, MD5, modification time, empty
      for a missing path or a folder), `openContent` (a stream body of the
      file's size that releases its handle when closed), `deploy` (hands the
      caller's stream to the normal upload path, which streams it into the
      binary store), and `move` and `copy` within one repository (another
      repository is refused with 400). The binding runs as the requesting
      user, never as the system: writes keep their usual per-path permission
      checks, and reads check the user's read permission on every path,
      because permission targets can be scoped to paths inside a repository
      while the shell's pre-check (A5) covers only the repository. A path the
      user may not read looks absent (`findFile`, `openContent`, `exists`,
      left out of `getChildren`) or is refused (`getContent`,
      `getStringContent`, `getFileInfo`, `getProperties`), so a plugin cannot
      serve, inspect or re-stream content its caller could not download.
      Evidence: `PluginRepositoriesApiTest#unreadablePathsLookAbsentAndNeverReachTheContentService`,
      `#childrenTheCallerMayNotReadAreLeftOut`,
      `OciRegistryPluginTest#blobsAndManifestsUnderAnUnreadableNameAreUnknown`,
      `#mountFromAnUnreadableNameInTheSameRepositoryFallsBackToAnUploadSession`,
      `PluginRepositoriesApiTest#findFileReportsSizeAndStoredSha256`,
      `#findFileIsEmptyForMissingPathsAndFolders`,
      `#openContentStreamsWithTheFileSizeAndReleasesTheHandle`,
      `#deployHandsTheCallersStreamToTheUploadPath`,
      `#moveAndCopyStayWithinTheRepository`.
- [x] A10. With no plugin type that has an API handler, Artifactory behaves as
      before: `/v2` stays unrewritten, the rewrite method is the configured or
      stock one, `/api/docker/` ignores Basic credentials as stock OSS does,
      and the existing suites pass.
      Evidence: `DockerInternalRewriteTest` (upstream, unchanged),
      `DockerInternalRewritePluginApiTest#withoutReverseProxyConfigurationAPluginApiSwitchesToRepoPathPrefix`,
      `PluginApiAuthenticationAdapterTest#withoutAnApiTypeOrOutsideTheEndpointNothingIsHandledAndSecurityIsNotConsulted`.
      Full suites on 2026-11-11 with this change: backend/core 10520 run 0
      failed (9 skipped), web/rest 1220 run 0 failed, web/rest-ui 3069 run 0
      failed (3 skipped), web/application 537 run 0 failed.

### OCI registry plugin

Phase 2 writes an OCI Distribution Spec v1.1 registry as a Groovy plugin on
the API above: `plugins/oci/oci-registry.groovy`, package type
`oci-registry` (the built-in `docker` and `oci` names are taken), described
in `plugins/oci/README.md`. It needed no Java change. Unit evidence is
`org.artifactory.addon.plugin.groovy.OciRegistryPluginTest` (backend/core),
which loads the shipped file into the plugin engine with the real
`repositories` binding over an in-memory repository
(`InMemoryRepositoryService`) and asserts after every test that no other
repository was touched and no content handle was left open. Live evidence is
`docker/it/run-oci-it.sh`, run on 2026-11-12 (UTC) against the overlay of
`releases-docker.jfrog.io/jfrog/artifactory-oss@sha256:d103c0464d33645acdcbfbb6fc61310d7faf14c9893975cbee562487eaeac1d1`
with docker 29.1.3 (classic image store) and podman 4.9.3 as clients: 63
assertions passed, 0 failed.

- [x] O1. `docker push` and `docker pull` of a single-platform image against
      `<host>/v2/<repoKey>/<name>` work after `docker login` with Artifactory
      credentials; the same with `podman`.
      Evidence: `run-oci-it.sh` `O1.docker-login`, `O1.docker-push`,
      `O1.docker-pull-by-tag-same-image-id`,
      `O1.docker-pull-by-digest-same-image-id`, `O1.podman-login`,
      `O1.podman-push`, `O1.podman-pull-by-tag-same-image-id`,
      `O1.podman-pull-by-digest-same-image-id`.
- [x] O2. Monolithic upload: `POST .../blobs/uploads/?digest=` in one request,
      and `POST` followed by a single `PUT ...?digest=`; the stored blob's
      SHA-256 is verified against the digest and a mismatch is refused.
      Evidence: `run-oci-it.sh` `O2.monolithic-post-201`,
      `O2.blob-get-exact-content`, `O2.blob-head-size`, `O2.blob-range-206`,
      `O2.post-then-put-201`, `O2.digest-mismatch-400-DIGEST_INVALID`,
      `O2.mismatched-content-not-stored`,
      `O2.large-64MiB-blob-streamed-round-trip`; unit
      `OciRegistryPluginTest#monolithicPostWithDigestStoresTheVerifiedBlobAndServesItWithExactHeaders`,
      `#postThenSinglePutWithDigestCompletesTheUpload`,
      `#digestMismatchDeletesTheUploadAndAnswersDigestInvalid`,
      `#invalidAndUnsupportedDigestsAreRefusedBeforeAnyStorageAccess`,
      `#rangeRequestsServeExactSlices`,
      `#contentAtADigestPathThatDoesNotMatchItsDigestIsNeitherServedNorTrusted`,
      `#concurrentPushOfTheSameBlobResolvesToTheVerifiedBlob`. An upload body
      sent as `application/x-www-form-urlencoded` is consumed by servlet
      parameter parsing in Artifactory's filters before the plugin runs, so it
      is refused as a digest mismatch (fails closed; registry clients send
      `application/octet-stream`).
- [x] O3. Chunked upload: `POST`, one or more `PATCH` with `Content-Range`,
      final `PUT ...?digest=`, with `Location`, `Range`, `Docker-Upload-UUID`
      and `OCI-Chunk-Min-Length` headers; out-of-order chunks are refused with
      416; upload status by `GET` on the upload URL.
      Evidence: `run-oci-it.sh` `O3.post-202-session-headers`,
      `O3.out-of-order-chunk-416`, `O3.first-chunk-202-range`,
      `O3.upload-status-204`, `O3.second-chunk-202-range`,
      `O3.final-put-201`, `O3.assembled-blob-matches`,
      `O3.finished-session-gone-404`; unit
      `OciRegistryPluginTest#chunkedUploadChecksOrderReportsStatusAndAssemblesTheBlob`,
      `#streamedPatchOfUnknownLengthAndFinalChunkInPutAreAppended`,
      `#chunkWhoseLengthDisagreesWithItsRangeIsRefused`,
      `#uploadSessionsAreStrictUuidsOwnedByTheirCreator`.
- [x] O4. Cross-repository blob mount `POST .../blobs/uploads/?mount=&from=`
      (optional: falling back to 202 and a normal upload is conformant).
      Mounts are served only from names in the same Artifactory repository;
      any other `from` gets an upload session, because following it would
      bypass the other repository's read permission.
      Evidence: `run-oci-it.sh` `O4.mount-same-repository-201`,
      `O4.mounted-blob-served`,
      `isolation.cross-repository-mount-falls-back-to-202`; unit
      `OciRegistryPluginTest#mountFromANameInTheSameRepositoryCreatesTheBlob`,
      `#mountFromAnotherRepositoryNeverReadsItAndFallsBackToAnUploadSession`.
- [x] O5. Manifests by tag and by digest: `PUT`, `GET` and `HEAD`, with
      `Docker-Content-Digest`, the stored media type as `Content-Type`, and
      both OCI and Docker schema 2 manifests.
      Evidence: `run-oci-it.sh` `O5.get-by-tag-media-type-and-digest`,
      `O5.head-by-digest-exact-length`, `O5.put-by-tag-201`,
      `O5.podman-docker-schema2-push-served-as-schema2`,
      `O5.missing-blob-400-MANIFEST_BLOB_UNKNOWN`,
      `O5.oversize-manifest-413`; unit
      `OciRegistryPluginTest#ociManifestByTagAndDigestWithItsMediaTypeAndDigestHeaders`,
      `#manifestWithoutMediaTypeIsServedWithTheContentTypeItWasPushedWith`,
      `#manifestReferencingAMissingBlobOrABlobOfAnotherNameIsRefused`,
      `#invalidManifestsAreRefused`,
      `#oversizeManifestIsRefusedWith413WithoutBufferingIt`,
      `#retaggingNeedsOverwritePermissionAndReportsDenied`.
- [ ] O6. Image index and Docker manifest list (multi-platform images) push
      and pull, including `docker buildx` multi-platform push.
      Shown except for `docker buildx`, which is not installed on the test
      host (and the classic docker image store cannot push a multi-platform
      build without a BuildKit container): podman pushes a two-platform image
      as an OCI index and as a Docker manifest list, both are served with
      their media type and resolvable children, and podman pulls the arm64
      image from the index (`run-oci-it.sh` `O6.podman-multi-platform-push`,
      `O6.oci-list-served-with-its-media-type`,
      `O6.docker-list-served-with-its-media-type`,
      `O6.podman-pull-selects-arm64-from-index`; unit
      `OciRegistryPluginTest#dockerSchema2ManifestAndManifestListAndOciIndex`).
- [x] O7. Tag listing `GET .../tags/list` with `n` and `last` pagination and
      the `Link` header.
      Evidence: `run-oci-it.sh` `O7.first-page-and-link`,
      `O7.next-page-follows-link`, `O7.last-page-without-link`,
      `O7.full-list-sorted`; unit
      `OciRegistryPluginTest#tagListIsSortedAndPaginatedWithALinkHeader`.
- [x] O8. Deletion of manifests by digest, tags, and blobs, answering 202, and
      404 afterwards.
      Evidence: `run-oci-it.sh` `O8.delete-tag-202`, `O8.deleted-tag-404`,
      `O8.delete-manifest-by-digest-202`, `O8.deleted-manifest-404`,
      `O8.delete-blob-202`, `O8.deleted-blob-404`; unit
      `OciRegistryPluginTest#deleteByTagThenByDigestRemovesTagsAndAnswers404Afterwards`,
      `#deleteBlobAnswers202ThenThe404`.
- [x] O9. The OCI distribution-spec conformance suite passes its pull, push,
      content discovery and content management workflows against the
      plugin.
      Evidence: `run-oci-it.sh` `O9.conformance-pull-push-discovery-management`
      runs `ghcr.io/opencontainers/distribution-spec/conformance@sha256:287c9c1cb6511da2789b5c2dc3f0f8285c2fb73e031a634f86dccd52556a6ca5`
      (v1.1.0) with all four workflows enabled,
      `OCI_NAMESPACE=oci-local/conformance`,
      `OCI_CROSSMOUNT_NAMESPACE=oci-local/crossmount` (the first segment is
      the repository key) and `OCI_AUTOMATIC_CROSSMOUNT=false`: 76 of 80
      specs ran, 76 passed, 0 failed. The 4 skipped specs are alternatives
      the suite skips by configuration: "Get tag name from environment" and
      "Populate registry with test tags (no push)" (the suite pushes its own
      data), "Cross-mounting of nonexistent blob should yield session id"
      (runs only when the mount answered 202; it answered 201) and
      "Cross-mounting without from, and automatic content discovery enabled
      should return a 201" (automatic mounting is not implemented; its
      "disabled should return a 202" counterpart passed). The suite's content
      discovery workflow covers the referrers API, also unit tested by
      `OciRegistryPluginTest#referrersListManifestsWithASubjectAndFilterByArtifactType`.
- [x] O10. Permissions hold end to end: a read-only user can pull but not
      push (403), an anonymous client is challenged (401), and a user without
      delete permission cannot delete.
      Evidence: `run-oci-it.sh` `O10.anonymous-ping-challenged-401`,
      `O10.anonymous-pull-challenged-401`, `O10.reader-can-pull`,
      `O10.reader-podman-push-refused`, `O10.reader-push-403`,
      `O10.reader-delete-403`,
      `O10.deployer-can-push-without-delete-permission`,
      `O10.user-without-delete-permission-cannot-delete-403`. Isolation:
      `isolation.path-traversal-does-not-leak`,
      `isolation.reader-has-no-access-to-oci-other`,
      `isolation.direct-deploy-refused`; unit
      `OciRegistryPluginTest#invalidNamesAndPathTraversalAreRefusedBeforeAnyStorageAccess`,
      `#directHttpDeploysIntoTheRepositoryAreRefusedButInternalDeploysPass`.
      Users and permission targets are created through the UI REST API
      (`/artifactory/ui/users`, `/artifactory/ui/permissiontargets`), because
      the public security API is a Pro feature in OSS.

Out of scope for the plugin: garbage collection of unreferenced blobs and of
abandoned upload sessions, remote and virtual repositories of the type,
token authentication (Basic only), `/v2/_catalog`, and automatic mounting
without `from`.

## Out of scope for the first iteration

- Remote and virtual repositories of plugin types (the `HttpRepoFactory` hook
  exists and can come later).
- UI listing of plugin types. The Angular frontend is not in the OSS sources.
- Realms, jobs, staging and promotion DSL blocks.
- WASM plugins.
